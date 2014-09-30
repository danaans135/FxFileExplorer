package fxfileexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class FxFileExplorer extends Application {

    @Override
    public void start(Stage stage) throws Exception {
//        Parent root = FXMLLoader.load(getClass().getResource("FxFileExplorer.fxml"));
        FXMLLoader loader = new FXMLLoader(getClass().getResource("FxFileExplorer.fxml"));
        Parent root = loader.load();
        final FxFileExplorerController ctrl = loader.getController();
        Scene scene = new Scene(root);
        initKeyEvent(ctrl, scene);
        stage.setTitle("FxFileExplorer");
        stage.setScene(scene);
        stage.show();

        Stage dialog = new Stage(StageStyle.UTILITY);
        Parent dr = FXMLLoader.load(getClass().getResource("BookmarkDialog.fxml"));
        dialog.setScene(new Scene(dr));
        dialog.show();
    }

    private void initKeyEvent(final FxFileExplorerController ctrl, Scene scene) {
        scene.setOnKeyReleased(ev -> {
//            System.out.println(ev.toString());
            if (ev.getCode() == KeyCode.J) {
                ctrl.dirTree.getSelectionModel().selectNext();
//                ctrl.dirTree.scrollTo(ctrl.dirTree.getSelectionModel().getSelectedIndex());
            } else if (ev.getCode() == KeyCode.K) {
                ctrl.dirTree.getSelectionModel().selectPrevious();
            } else if (ev.getCode() == KeyCode.L) {
                ctrl.dirTree.getSelectionModel().getSelectedItem().setExpanded(true);
            } else if (ev.getCode() == KeyCode.H) {
                TreeItem<String> selectedItem = ctrl.dirTree.getSelectionModel().getSelectedItem();
                if (selectedItem.isExpanded()) {
                    selectedItem.setExpanded(false);
                } else {
                    ctrl.dirTree.getSelectionModel().select(selectedItem.getParent());
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(FxFileExplorer.class);
    }

}
