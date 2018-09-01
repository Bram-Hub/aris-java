package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.ModuleService;
import edu.rpi.aris.assign.Problem;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.client.dialog.ImportProblemsDialog;
import edu.rpi.aris.assign.client.dialog.ProblemDialog;
import edu.rpi.aris.assign.client.model.Config;
import edu.rpi.aris.assign.client.model.Problems;
import edu.rpi.aris.assign.client.model.UserInfo;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class ProblemsGui implements TabGui {

    private static final FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter("All Files", "*");
    @FXML
    private TableView<Problems.Problem> problemTbl;
    @FXML
    private TableColumn<Problems.Problem, String> name;
    @FXML
    private TableColumn<Problems.Problem, String> module;
    @FXML
    private TableColumn<Problems.Problem, String> createdBy;
    @FXML
    private TableColumn<Problems.Problem, Date> createdOn;
    @FXML
    private TableColumn<Problems.Problem, Button> delete;
    private UserInfo userInfo = UserInfo.getInstance();
    private Problems problems = new Problems(this);
    private Parent root;

    public ProblemsGui() {
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("../view/problems_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public void load(boolean reload) {
        problems.loadProblems(reload);
    }

    @Override
    public void unload() {
        problems.clear();
    }

    @FXML
    public void initialize() {
        Label placeHolderLbl = new Label();
        problemTbl.setPlaceholder(placeHolderLbl);
        placeHolderLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (!userInfo.isLoggedIn())
                return "Not Logged In";
            else if (problems.isLoadError())
                return "Error Loading Problems";
            else
                return "No Problems Added";
        }, userInfo.loginProperty(), userInfo.loadingProperty(), problems.loadErrorProperty()));
        problemTbl.setItems(problems.getProblems());
        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                problems.clear();
        });
        name.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getName()));
        module.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getModule()));
        createdBy.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getCreatedBy()));
        createdOn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getCreatedOn()));
    }

    @FXML
    public <T extends ArisModule> void createProblem() {
        try {
            ProblemDialog<T> dialog = new ProblemDialog<>(AssignGui.getInstance().getStage());
            Optional<Triple<String, String, Problem<T>>> result = dialog.showAndWait();
            result.ifPresent(triple -> problems.createProblem(triple.getLeft(), triple.getMiddle(), triple.getRight()));
        } catch (IOException e) {
            LibAssign.showExceptionError(e);
        }
    }

    @FXML
    public void importProblem() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Import Problem Files");
        dialog.setHeaderText("Import Problem Files");
        VBox box = new VBox(5);
        box.getChildren().add(new Label("Select Module:"));
        ComboBox<String> modules = new ComboBox<>();
        modules.setMaxWidth(Double.MAX_VALUE);
        modules.getItems().addAll(ModuleService.getService().moduleNames());
        Collections.sort(modules.getItems());
        if (modules.getItems().size() == 1)
            modules.getSelectionModel().select(0);
        box.getChildren().add(modules);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(modules.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(param -> param == ButtonType.OK ? modules.getSelectionModel().getSelectedItem() : null);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::importModuleProblems);
    }

    private <T extends ArisModule> void importModuleProblems(String moduleName) {
        System.out.println("Import " + moduleName);
        ArisModule<T> module = ModuleService.getService().getModule(moduleName);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import " + moduleName + " files");
        try {
            List<String> exts = module.getProblemFileExtensions();
            boolean first = true;
            for (String ext : exts) {
                FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter(moduleName + " Problem File (." + ext + ")", "*." + ext);
                chooser.getExtensionFilters().add(extensionFilter);
                if (first) {
                    chooser.setSelectedExtensionFilter(extensionFilter);
                    first = false;
                }
            }
            chooser.getExtensionFilters().add(allFiles);
            chooser.setInitialDirectory(new File(Config.LAST_FILE_LOC.getValue()));
            List<File> importList = chooser.showOpenMultipleDialog(AssignGui.getInstance().getStage());
            importFiles(importList, module);
        } catch (Exception e) {
            LibAssign.showExceptionError(e);
        }
    }

    private <T extends ArisModule> void importFiles(List<File> files, ArisModule<T> module) {
        if (files == null || files.size() == 0)
            return;
        try {
            ProblemConverter<T> problemConverter = module.getProblemConverter();
            if (files.size() == 1) {
                File file = files.get(0);
                String name = file.getName();
                if (name.contains("."))
                    name = name.substring(0, name.lastIndexOf('.'));
                Problem<T> problem = problemConverter.loadProblem(new FileInputStream(file));
                ProblemDialog<T> dialog = new ProblemDialog<>(AssignGui.getInstance().getStage(), module.getModuleName(), name, problem);
                dialog.showAndWait();
            } else {
                ImportProblemsDialog<T> dialog = new ImportProblemsDialog<>(AssignGui.getInstance().getStage(), module.getModuleName(), files, problemConverter);
                dialog.showAndWait();
            }
        } catch (Exception e) {
            LibAssign.showExceptionError(e);
        }
    }

}
