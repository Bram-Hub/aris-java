package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.SingleAssignment;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

public class SingleAssignmentGui implements TabGui {

    private static final HashMap<String, Image> iconImages = new HashMap<>();
    private static final ModuleUIOptions SUBMIT_OPTIONS = new ModuleUIOptions(EditMode.RESTRICTED_EDIT, "Create Submission", true, false, true, true, false);
    private static final ModuleUIOptions READ_ONLY_OPTIONS = new ModuleUIOptions(EditMode.READ_ONLY, "View Submission", false, false, false, false, false);
    private final SingleAssignment assignment;
    private final boolean isInstructor;
    @FXML
    private TreeTableView<SingleAssignment.Submission> treeTable;
    @FXML
    private TreeTableColumn<SingleAssignment.Submission, String> nameColumn;
    @FXML
    private TreeTableColumn<SingleAssignment.Submission, String> submittedColumn;
    @FXML
    private TreeTableColumn<SingleAssignment.Submission, String> statusColumn;
    @FXML
    private TreeTableColumn<SingleAssignment.Submission, Node> buttonColumn;
    @FXML
    private Label name;
    @FXML
    private Label dueDate;
    @FXML
    private Label status;
    @FXML
    private ImageView statusIcon;
    @FXML
    private ProgressIndicator gradingIndicator;
    private CurrentUser userInfo = CurrentUser.getInstance();
    private Parent root;
    private TreeItem<SingleAssignment.Submission> rootItem = new TreeItem<>();

    SingleAssignmentGui(String name, int cid, int aid, boolean isInstructor) {
        this.isInstructor = isInstructor;
        assignment = new SingleAssignment(this, name, cid, aid, isInstructor);
        FXMLLoader loader = new FXMLLoader(SingleAssignmentGui.class.getResource("/edu/rpi/aris/assign/client/view/single_assignment.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @Override
    public void load(boolean reload) {
        assignment.loadAssignment(reload);
    }

    @Override
    public void unload() {
        assignment.clear();
    }

    @Override
    public void closed() {
        assignment.closed();
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return false;
    }

    @Override
    public String getName() {
        return assignment.getName();
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return assignment.nameProperty();
    }

    @Override
    public boolean requiresOnline() {
        return !assignment.isCached();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SingleAssignmentGui) {
            SingleAssignment a = ((SingleAssignmentGui) obj).assignment;
            return a.getCid() == assignment.getCid() && a.getAid() == assignment.getAid();
        } else
            return false;
    }

    public int getCid() {
        return assignment.getCid();
    }

    @FXML
    public void initialize() {
        Label placeHolder = new Label();
        treeTable.setPlaceholder(placeHolder);
        placeHolder.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (assignment.isLoadError())
                return "An error occurred loading the assignment";
            else
                return "No problems in assignment";
        }, userInfo.loadingProperty(), assignment.loadErrorProperty()));
        treeTable.setRoot(rootItem);
        treeTable.setShowRoot(false);
        assignment.getProblems().addListener((ListChangeListener<TreeItem<SingleAssignment.Submission>>) c -> {
            while (c.next()) {
                if (c.wasAdded())
                    rootItem.getChildren().addAll(c.getAddedSubList());
                if (c.wasRemoved())
                    rootItem.getChildren().removeAll(c.getRemoved());
            }
        });
        name.textProperty().bind(Bindings.createStringBinding(() -> assignment.getName() + ":", assignment.nameProperty()));
        dueDate.textProperty().bind(assignment.dueDateProperty());
        status.textProperty().bind(Bindings.createStringBinding(() -> userInfo.isLoggedIn() ? assignment.getStatusStr() : "Offline", assignment.statusStringProperty(), userInfo.loginProperty()));
        status.setManaged(!isInstructor);
        status.setVisible(!isInstructor);
        statusIcon.imageProperty().bind(Bindings.createObjectBinding(() -> assignment.getStatus() == null || !userInfo.isLoggedIn() ? null : iconImages.computeIfAbsent(assignment.getStatus().icon, str -> str == null ? null : new Image(SingleAssignmentGui.class.getResourceAsStream("/edu/rpi/aris/assign/client/images/" + str))), assignment.statusProperty(), userInfo.loginProperty()));
        statusIcon.setManaged(!isInstructor);
        statusIcon.setVisible(!isInstructor);
        gradingIndicator.setVisible(false);
        gradingIndicator.setManaged(false);
        if (!isInstructor) {
            statusIcon.visibleProperty().bind(assignment.statusProperty().isNotEqualTo(GradingStatus.GRADING).and(userInfo.loginProperty()));
            statusIcon.managedProperty().bind(statusIcon.visibleProperty());
            gradingIndicator.visibleProperty().bind(statusIcon.visibleProperty().not().and(userInfo.loginProperty()));
            gradingIndicator.managedProperty().bind(gradingIndicator.visibleProperty());
        }
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
        nameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        submittedColumn.setCellValueFactory(param -> param.getValue().getValue().submittedOnProperty());
        submittedColumn.setStyle("-fx-alignment: CENTER;");
        statusColumn.setCellValueFactory(param -> param.getValue().getValue().statusStrProperty());
        statusColumn.setStyle("-fx-alignment: CENTER;");
        buttonColumn.setCellValueFactory(param -> param.getValue().getValue().controlNodeProperty());
        buttonColumn.setStyle("-fx-alignment: CENTER;");

    }

    public <T extends ArisModule> void createAttempt(SingleAssignment.Attempt problemInfo, String problemName, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        if (clientModule == null) {
            AssignClient.displayErrorMsg("Missing client module", "Client module is missing for the following module: \"" + module.getModuleName() + "\"");
            return;
        }
        ModuleUI<T> moduleUI = clientModule.createModuleGui(SUBMIT_OPTIONS, problem);
        moduleUI.setDescription("Modify attempt for problem: \"" + problemName + "\"");
        moduleUI.setModuleUIListener(new ModuleUIAdapter() {

            @Override
            public boolean guiCloseRequest(boolean hasUnsavedChanges) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Submit Problem?");
                alert.setHeaderText("Would you like to submit this problem?");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                Window window = moduleUI.getUIWindow();
                if (window != null) {
                    alert.initOwner(window);
                    alert.initModality(Modality.WINDOW_MODAL);
                } else {
                    alert.initOwner(AssignGui.getInstance().getStage());
                    alert.initModality(Modality.APPLICATION_MODAL);
                }
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    uploadProblem();
                } else if (hasUnsavedChanges) {
                    alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("You have unsaved changes that will be lost. Are you sure you want to exit?");
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    if (window != null) {
                        alert.initOwner(window);
                        alert.initModality(Modality.WINDOW_MODAL);
                    } else {
                        alert.initOwner(AssignGui.getInstance().getStage());
                        alert.initModality(Modality.APPLICATION_MODAL);
                    }
                    result = alert.showAndWait();
                    return result.isPresent() && result.get() == ButtonType.YES;
                }
                return true;
            }

            @Override
            public boolean saveProblemLocally() {
                return assignment.saveAttempt(problemInfo, problem, module, false);
            }

            @Override
            public void uploadProblem() {
                assignment.uploadAttempt(problemInfo, problem, module);
                try {
                    moduleUI.hide();
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            }
        });
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

    public <T extends ArisModule> void viewProblem(String description, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        if (clientModule == null) {
            AssignClient.displayErrorMsg("Missing client module", "Client module is missing for the following module: \"" + module.getModuleName() + "\"");
            return;
        }
        ModuleUI<T> moduleUI = clientModule.createModuleGui(READ_ONLY_OPTIONS, problem);
        moduleUI.setDescription(description);
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

    public <T extends ArisModule> void viewSubmission(SingleAssignment.Submission submission, String problemName, Problem<T> problem, ArisModule<T> module) throws Exception {
        viewProblem("Viewing " + submission.getName() + (problemName == null ? "" : " for problem: \"" + problemName + "\"") + " (read only)", problem, module);
    }

}
