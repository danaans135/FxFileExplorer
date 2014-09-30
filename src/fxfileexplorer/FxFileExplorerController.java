package fxfileexplorer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;


public class FxFileExplorerController {

    @FXML
    public TextField pathTextField;

    @FXML
    private TableView<Path> fileTable;

    @FXML
    public TreeView<String> dirTree;

    @FXML
    private Button favoriteButton;

    private Map<Path, Image> iconImgMap = new HashMap<>();

    private StringProperty curPath = new SimpleStringProperty();

    private boolean isDragFileTable = false;
    public StringProperty curPathProperty() { return curPath; }
    public void setCurPath(String curPath) { this.curPath.set(curPath); }
    public String getCurPath() { return curPath.get(); }

    private final class PathTreeItem extends TreeItem<String> {
        protected Path myPath;

        public PathTreeItem(Path path) {
            super(path.toString());
            this.myPath = path;

            if (!path.toString().endsWith(File.separator)) {
                String value = path.toString();
                int indexOf = value.lastIndexOf(File.separator);
                if (indexOf > 0) {
                    this.setValue(value.substring(indexOf + 1));
                } else {
                    this.setValue(value);
                }
            }

            // システムアイコン設定（非同期）
//            Image img = getSystemIcon(path);
//            this.setGraphic(new ImageView(img));
            CompletableFuture
                .supplyAsync(() -> Utils.getSystemIcon(path))
                .whenComplete((res, e) -> {
                    Platform.runLater(() -> this.setGraphic(new ImageView(res)));
                });


            // ツリーノード展開時イベント
            addEventHandler(TreeItem.branchExpandedEvent(), new EventHandler<TreeItem.TreeModificationEvent<String>>() {
                @Override
                public void handle(TreeItem.TreeModificationEvent<String> ev) {
                    PathTreeItem source = (PathTreeItem)ev.getSource();
                    if (!source.myPath.equals(myPath)) return;
                    if (source.getChildren().isEmpty()) {
                        if (!source.isLeaf()) {
                            try {
                                DirectoryStream<Path> dir=Files.newDirectoryStream(myPath);
                                for (Path file : dir) {
                                    if (Files.isDirectory(file)) {
                                        source.getChildren().add(new PathTreeItem(file));
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }

        Map<Path, Boolean> leafMap = new HashMap<>();

        @Override
        public boolean isLeaf() {
            if (leafMap.get(myPath) != null) {
                return leafMap.get(myPath);
            }
            boolean isSubDir = false;
            try {
                DirectoryStream<Path> dir=Files.newDirectoryStream(myPath);
                for (Path file : dir) {
                    if (Files.isDirectory(file)) {
                        isSubDir = true;
                        break;
                    }
                }
            } catch (IOException e) {
                if ((e instanceof AccessDeniedException) || e instanceof FileSystemException) {
                } else {
                    e.printStackTrace();
                }
            }
            boolean ret = !Files.isDirectory(myPath) || !isSubDir;
            leafMap.put(myPath, ret);
            return ret;
        }

    }

    @FXML
    void initialize() {
        // ツリー初期化
        initDirTree();

        // アドレス欄初期化
        pathTextField.textProperty().bindBidirectional(curPath);

        favoriteButton.setOnAction(ev -> onActionFavoriteButton(ev));

        // プロパティ初期化
        initProperty();

        // テーブル初期化
        initFileTable();
    }

    private void onActionFavoriteButton(ActionEvent ev) {
        Bookmark.getInstance().add(getCurPath());
    }

    private void initProperty() {
        // カレントパス変更時イベント
        curPath.addListener((observable, oldValue, newValue) -> {
            iconImgMap.clear();
            ObservableList<Path> l = FXCollections.observableArrayList();
            try {
                DirectoryStream<Path> dir=Files.newDirectoryStream(Paths.get(newValue));
                for (Path file : dir) {
                    l.add(file);
                }
            } catch (IOException e) {
                // e.printStackTrace();
                System.err.println("bad!");
            }
            ObservableList<Path> l2 = FXCollections.observableArrayList();
            l
                .stream()
                .sorted((e1, e2) -> {
                    if (Files.isDirectory(e1) != Files.isDirectory(e2)) {
                        return (Files.isDirectory(e1)) ? -1 : 1; // Dirを上に
                    }
                    return e1.compareTo(e2);
                })
                .forEach(value -> l2.add(value));
            fileTable.setItems(l2);
        });
    }

    private void initFileTable() {
        fileTable.getColumns().clear();

        TableColumn<Path, Path> column1 = new TableColumn<Path, Path>("名前");
        column1.setCellValueFactory(arg0 -> {
            return new ObjectBinding<Path>() {
                @Override
                protected Path computeValue() {
                    return arg0.getValue();
                }
            };
        });
        column1.setCellFactory(param -> {
            TableCell<Path, Path> cell = new TableCell<Path, Path>() {
                @Override
                protected void updateItem(Path item, boolean empty) {
                    if (item != null) {
                        HBox hbox = new HBox(4);
                        ImageView imageView = new ImageView();
                        hbox.getChildren().add(imageView);
                        if (iconImgMap.get(item) != null) {
                            imageView.setImage(iconImgMap.get(item));
                        } else {
                            ImageIcon icon = (ImageIcon) FileSystemView.getFileSystemView().getSystemIcon(item.toFile());
                            java.awt.Image image = icon.getImage();
                            imageView.setFitWidth(image.getWidth(null));
                            CompletableFuture
                            .supplyAsync(() -> Utils.getSystemIcon(item))
                            .whenComplete((res, e) -> {
                                iconImgMap.put(item, res);
                                Platform.runLater(() -> imageView.setImage(res));
                            });
                        }

                        hbox.getChildren().add(new Label(item.getFileName().toString()));
                        setGraphic(hbox);
                        setItem(item);
                    } else {
                        setGraphic(null);
                        setItem(null);
                    }
                }
            };
//            cell.setOnMouseEntered(ev -> {
//                System.out.println("m ent");
//                if (isDragFileTable) {
//                    cell.setStyle("-fx-background-color: red;");
//                }
//            });
            cell.setOnDragEntered(ev -> {
                System.out.println("c dr en");
                if (ev.getDragboard().hasFiles()) {
                    if (cell.getItem() != null && Files.isDirectory(cell.getItem())) {
                        cell.setStyle("-fx-background-color: cyan;");
                    }
                }
            });
            cell.setOnDragExited(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    if (cell.getItem() != null && Files.isDirectory(cell.getItem())) {
                        cell.setStyle("-fx-background-color: transparent;");
                    }
                }
            });
            cell.setOnDragOver(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    ev.consume();
                }
            });
            cell.setOnDragDropped(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    System.out.println("dropped:"+ev.getDragboard().getFiles());
                }
            });
            return cell;
        });
//        column1.setCellFactory(new Callback<TableColumn<Path,Path>, TableCell<Path,Path>>() {
//
//            @Override
//            public TableCell<Path, Path> call(TableColumn<Path, Path> arg0) {
//                return new TableCell<Path, Path>() {
//                    @Override
//                    protected void updateItem(Path item, boolean empty) {
//                        super.updateItem(item, empty);
//                        if (item == null || empty) {
//                            setText(null);
//                        }
//                    }
//                };
//            }
//        });
        column1.setPrefWidth(200);
        fileTable.getColumns().add(column1);

        TableColumn<Path, String> column2 = new TableColumn<Path, String>("サイズ");
        column2.setCellValueFactory(arg0 -> {
            return new StringBinding() {
                @Override
                protected String computeValue() {
                    try {
                        return String.valueOf(Files.size(arg0.getValue()));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return "-";
                    }
                }
            };
        });

        fileTable.getColumns().add(column2);
//        ObservableList<Path> l = FXCollections.observableArrayList(FileSystems.getDefault().getPath("c:\\temp"));
//        fileTable.setItems(l);

        fileTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent ev) {
                System.out.println(ev);
                if (ev.getClickCount() == 2 && fileTable.getSelectionModel().getSelectedItem() != null) {
                    Path path = fileTable.getSelectionModel().getSelectedItem();
                    if (Files.isDirectory(path)) {
                        setCurPath(path.toString());
                    } else {
                        try {
                            Desktop.getDesktop().open(path.toFile());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        });
        fileTable.setOnKeyReleased(ev -> {
            if (ev.getCode() == KeyCode.RIGHT) {
                Path path = fileTable.getSelectionModel().getSelectedItem();
                if (path != null && Files.isDirectory(path)) {
                    setCurPath(path.toString());
                    fileTable.getSelectionModel().selectFirst();
                    fileTable.scrollTo(0);
                }
            } else if (ev.getCode() == KeyCode.LEFT) {
                Path oldPath = Paths.get(getCurPath());
                setCurPath(oldPath.getParent().toString());
                fileTable.getSelectionModel().select(oldPath);
                fileTable.scrollTo(oldPath);
            }
        });

//        fileTable.setOnDragEntered(ev -> {
//            System.out.println("dragenter");
//            isDragFileTable  = true;
//        });
//        fileTable.setOnDragDone(ev -> {
//            System.out.println("drag done");
//            isDragFileTable  = false;
//        });
//        fileTable.setOnDragOver(ev -> {
//            ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
//            ev.consume();
//        });
//        fileTable.setOnDragDropped(ev -> {
//            System.out.println("dropped:"+ev.getDragboard().getFiles());
//            isDragFileTable  = false;
//        });
    }

    private void initDirTree() {
        TreeItem<String> rootItem = new TreeItem<String>("root");
        for (Path p : FileSystems.getDefault().getRootDirectories()) {
            TreeItem<String> treeItem = new PathTreeItem(p);
            rootItem.getChildren().add(treeItem);
        }
//        dirTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
//            if (newValue != null) {
////                System.out.println("selchan: "+newValue.getValue());
//               PathTreeItem item = (PathTreeItem)newValue;
//               setCurPath(item.myPath.toString());
//            }
//         });
        dirTree.setOnKeyReleased(ev -> {
            System.out.println("##"+ev.toString());
            if (ev.getCode() == KeyCode.ENTER) {

                PathTreeItem selectedItem = (PathTreeItem)dirTree.getSelectionModel().getSelectedItem();
//                System.out.println(selectedItem.getValue());
                setCurPath(selectedItem.myPath.toString());
            }
        });
        dirTree.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                PathTreeItem selectedItem = (PathTreeItem)dirTree.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    setCurPath(selectedItem.myPath.toString());
                }
            }
        });
        dirTree.setRoot(rootItem);
        dirTree.setShowRoot(false);
    }

}
