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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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

    @FXML
    public Button backButton;

    @FXML
    public Button nextButton;

    @FXML
    public SplitPane mainSplit;

    private Map<Path, Image> iconImgCache = new HashMap<>();

    private StringProperty curPath = new SimpleStringProperty();

    private boolean isDragFileTable = false;

    private History history = new History();

    public StringProperty curPathProperty() { return curPath; }
    public void setCurPath(String curPath) { this.curPath.set(curPath); }
    public String getCurPath() { return curPath.get(); }

    private final class TableCellExtension extends TableCell<Path, Path> {
        public TableCellExtension() {
            setOnDragEntered(ev -> {
                System.out.println("c dr en");
                if (ev.getDragboard().hasFiles()) {
                    if (getItem() != null && Files.isDirectory(getItem())) {
                        setStyle("-fx-background-color: cyan;");
                    }
                }
            });
            setOnDragExited(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    if (getItem() != null && Files.isDirectory(getItem())) {
                        setStyle("-fx-background-color: transparent;");
                    }
                }
            });
            setOnDragOver(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    ev.consume();
                }
            });
            setOnDragDropped(ev -> {
                if (ev.getDragboard().hasFiles()) {
                    System.out.println("dropped:"+ev.getDragboard().getFiles());
                }
            });
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            if (item != null) {
                ImageView imageView = new ImageView();
                if (iconImgCache.get(item) != null) {
                    // アイコン設定
                    imageView.setImage(iconImgCache.get(item));
                } else {
                    // アイコンイメージ幅を設定
                    ImageIcon icon = (ImageIcon) FileSystemView.getFileSystemView().getSystemIcon(item.toFile());
                    java.awt.Image image = icon.getImage();
                    imageView.setFitWidth(image.getWidth(null));
                    // アイコン設定（非同期）
                    Executor exec = Executors.newSingleThreadExecutor();
                    CompletableFuture
                        .supplyAsync(() -> Utils.getSystemIcon(item), exec)
                        .whenComplete((res, e) -> {
                            iconImgCache.put(item, res);
                            Platform.runLater(() -> imageView.setImage(res));
                        });
                }
                setGraphic(imageView);
                setText(item.getFileName().toString());
                setItem(item);
            } else {
                setGraphic(null);
                setText(null);
                setItem(null);
            }
        }
    }

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

        backButton.setOnAction(ev -> onActionBackButton(ev));
        nextButton.setOnAction(ev -> onActionNextButton(ev));
        favoriteButton.setOnAction(ev -> onActionFavoriteButton(ev));
        SplitPane.setResizableWithParent(mainSplit.getItems().get(0), false);

        // プロパティ初期化
        initProperty();

        // テーブル初期化
        initFileTable();
    }

    private void onActionNextButton(ActionEvent ev) {
        Path path = history.next();
        setCurPath(path.toString());
    }
    private void onActionBackButton(ActionEvent ev) {
        Path path = history.back();
        setCurPath(path.toString());
    }
    private void onActionFavoriteButton(ActionEvent ev) {
        Bookmark.getInstance().add(getCurPath());
    }

    private void initProperty() {
        // カレントパス変更時イベント
        curPath.addListener((observable, oldValue, newValue) -> {
            iconImgCache.clear();
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
            l.stream()
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

        // 名前列
        TableColumn<Path, Path> column1 = new TableColumn<Path, Path>("名前");
        column1.setCellValueFactory(param -> {
            return new ObjectBinding<Path>() {
                @Override
                protected Path computeValue() {
                    return param.getValue();
                }
            };
        });
        column1.setCellFactory(param -> {
            return new TableCellExtension();
        });
        column1.setPrefWidth(300);
        fileTable.getColumns().add(column1);

        // サイズ列
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

        // マウスクリック時イベント
        fileTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent ev) {
                // 項目をダブルクリックした場合
                if (ev.getClickCount() == 2 && fileTable.getSelectionModel().getSelectedItem() != null) {
                    Path path = fileTable.getSelectionModel().getSelectedItem();
                    if (Files.isDirectory(path)) {
                        setCurPath(path.toString());
                        history.add(path);
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

        // キーリリース時イベント
        fileTable.setOnKeyReleased(ev -> {
            if (ev.getCode() == KeyCode.RIGHT && !ev.isAltDown()) {
                Path path = fileTable.getSelectionModel().getSelectedItem();
                if (path != null && Files.isDirectory(path)) {
                    setCurPath(path.toString());
                    history.add(path);
                    fileTable.getSelectionModel().selectFirst();
                    fileTable.scrollTo(0);
                }
            } else if (ev.getCode() == KeyCode.LEFT && !ev.isAltDown()) {
                Path oldPath = Paths.get(getCurPath());
                setCurPath(oldPath.getParent().toString());
                history.add(oldPath.getParent());
                fileTable.getSelectionModel().select(oldPath);
                fileTable.scrollTo(oldPath);
            }
        });
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
                history.add(selectedItem.myPath);
            }
        });
        dirTree.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                PathTreeItem selectedItem = (PathTreeItem)dirTree.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    setCurPath(selectedItem.myPath.toString());
                    history.add(selectedItem.myPath);
                }
            }
        });
        dirTree.setRoot(rootItem);
        dirTree.setShowRoot(false);
    }

}
