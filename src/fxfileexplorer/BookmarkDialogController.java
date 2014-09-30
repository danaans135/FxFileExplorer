package fxfileexplorer;

import fxfileexplorer.Bookmark.BookmarkElement;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

public class BookmarkDialogController {

    @FXML
    private TableView<BookmarkElement> bookmarkTable;

    @FXML
    private TableColumn<BookmarkElement, String> pathColumn;

    @FXML
    void initialize() {
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        bookmarkTable.setItems(Bookmark.getInstance().getElementList());
    }

}
