package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.ClassCreateMsg;
import edu.rpi.aris.assign.message.ClassDeleteMsg;
import edu.rpi.aris.assign.message.ConnectionInitMsg;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class CurrentUser implements ResponseHandler<ConnectionInitMsg> {

    private static final CurrentUser instance = new CurrentUser();
    private static final Logger log = LogManager.getLogger();

    private SimpleObjectProperty<ServerRole> defaultRole = new SimpleObjectProperty<>();
    private SimpleObjectProperty<ServerRole> classRole = new SimpleObjectProperty<>();
    private ObservableList<ClassInfo> classes = FXCollections.observableArrayList();
    private SimpleBooleanProperty loggedIn = new SimpleBooleanProperty();
    private SimpleIntegerProperty loading = new SimpleIntegerProperty();
    private SimpleObjectProperty<ClassInfo> selectedClass = new SimpleObjectProperty<>();

    private ClassCreateResponseHandler createHandler = new ClassCreateResponseHandler();
    private ClassDeleteResponseHandler deleteHandler = new ClassDeleteResponseHandler();

    private HashMap<Integer, ClassInfo> classMap = new HashMap<>();
    private User user;
    private ReentrantLock lock = new ReentrantLock(true);
    private Runnable onLoad;

    private CurrentUser() {
        selectedClass.addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                LocalConfig.SELECTED_COURSE_ID.setValue(newValue.getClassId());
        });
        classRole.bind(Bindings.createObjectBinding(() -> defaultRole.get() == null ? null : (selectedClass.get() == null ? defaultRole.get() : selectedClass.get().getUserRole()), selectedClass, defaultRole));
    }

    public static CurrentUser getInstance() {
        return instance;
    }

    public ObservableList<ClassInfo> classesProperty() {
        return classes;
    }

    public BooleanBinding loadingBinding() {
        return loading.greaterThan(0);
    }

    public ObservableIntegerValue loadingProperty() {
        return loading;
    }

    public SimpleBooleanProperty loginProperty() {
        return loggedIn;
    }

    public SimpleObjectProperty<ServerRole> defaultRoleProperty() {
        return defaultRole;
    }

    public SimpleObjectProperty<ClassInfo> selectedClassProperty() {
        return selectedClass;
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    public boolean isLoading() {
        return loading.get() > 0;
    }

    public void startLoading() {
        Platform.runLater(() -> loading.set(loading.get() + 1));
    }

    public void finishLoading() {
        Platform.runLater(() -> loading.set(loading.get() - 1));
    }

    public ServerRole getDefaultRole() {
        return defaultRole.get();
    }

    public synchronized void connectionInit(Runnable onLoad) {
        if (loggedIn.get())
            onLoad.run();
        else {
            if (lock.isLocked())
                return;
            this.onLoad = onLoad;
            Client.getInstance().processMessage(new ConnectionInitMsg(), this);
        }
    }

    public synchronized void logout() {
        loggedIn.set(false);
        classes.clear();
        classMap.clear();
        defaultRole.set(null);
        LocalConfig.USERNAME.setValue(null);
        LocalConfig.ACCESS_TOKEN.setValue(null);
    }

    public void createClass(String name) {
        Client.getInstance().processMessage(new ClassCreateMsg(name), createHandler);
    }

    public void deleteClass(int classId) {
        Client.getInstance().processMessage(new ClassDeleteMsg(classId), deleteHandler);
    }

    public User getUser() {
        return user;
    }

    @Override
    public void response(ConnectionInitMsg message) {
        Platform.runLater(() -> {
            ServerConfig.setPermissions(message.getPermissions());
            user = new User(message, LocalConfig.USERNAME.getValue());
            defaultRole.set(message.getDefaultRole());
            loggedIn.set(true);
            classes.clear();
            classMap.clear();
            message.getClassNames().forEach((k, v) -> {
                ClassInfo info = new ClassInfo(k, v, message.getPermissions() == null ? null : message.getPermissions().getRole(message.getClassRoles().get(k)));
                classes.add(info);
                classMap.put(k, info);
            });
            OfflineDB.submit(con -> {
                try (PreparedStatement select = con.prepareStatement("SELECT count(*) FROM classes WHERE cid = ?;");
                     PreparedStatement insert = con.prepareStatement("INSERT INTO classes (cid, name) VALUES (?, ?);");
                     PreparedStatement update = con.prepareStatement("UPDATE classes SET name = ? WHERE cid = ?;")) {
                    for (Map.Entry<Integer, String> e : message.getClassNames().entrySet()) {
                        select.setInt(1, e.getKey());
                        try (ResultSet rs = select.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                update.setString(1, e.getValue());
                                update.setInt(2, e.getKey());
                                update.execute();
                            } else {
                                insert.setInt(1, e.getKey());
                                insert.setString(2, e.getValue());
                                insert.execute();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("An error occurred updating the classes in the offline database", e);
                }
            });
            Collections.sort(classes);
            selectedClass.set(classMap.get(LocalConfig.SELECTED_COURSE_ID.getValue()));
            if (selectedClass.get() == null && classes.size() > 0)
                selectedClass.set(classes.get(0));
            if (onLoad != null)
                onLoad.run();
            onLoad = null;
        });
    }

    @Override
    public void onError(boolean suggestRetry, ConnectionInitMsg msg) {
        Platform.runLater(() -> {
            loggedIn.set(false);
            classes.clear();
            classMap.clear();
            if (suggestRetry)
                connectionInit(onLoad);
            else
                onLoad = null;
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public ClassInfo getSelectedClass() {
        return selectedClass.get();
    }

    public ServerRole getClassRole() {
        return classRole.get();
    }

    public SimpleObjectProperty<ServerRole> classRoleProperty() {
        return classRole;
    }

    public static class ClassStringConverter extends StringConverter<ClassInfo> {
        @Override
        public String toString(ClassInfo object) {
            return object.getClassName();
        }

        @Override
        public ClassInfo fromString(String string) {
            return null;
        }
    }

    private class ClassDeleteResponseHandler implements ResponseHandler<ClassDeleteMsg> {

        @Override
        public void response(ClassDeleteMsg message) {
            Platform.runLater(() -> {
                ClassInfo info = classMap.get(message.getClassId());
                classes.remove(info);
                if (classes.size() > 0)
                    selectedClass.set(classes.get(0));
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassDeleteMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ClassCreateResponseHandler implements ResponseHandler<ClassCreateMsg> {

        @Override
        public void response(ClassCreateMsg message) {
            Platform.runLater(() -> {
                ClassInfo info = new ClassInfo(message.getClassId(), message.getClassName(), defaultRole.get());
                classMap.put(message.getClassId(), info);
                classes.add(info);
                Collections.sort(classes);
                selectedClass.set(info);
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassCreateMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

}
