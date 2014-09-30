package fxfileexplorer;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Bookmark {
    public class BookmarkElement {

        private String mPath;

        public String getPath() {
            return mPath;
        }

        public void setPath(String path) {
            mPath = path;
        }

        public BookmarkElement(String path) {
            mPath = path;
        }

    }

    private static Bookmark instance = new Bookmark();
    private ObservableList<BookmarkElement> mElementList = FXCollections.observableArrayList();
    public ObservableList<BookmarkElement> getElementList() {
        return mElementList;
    }
    public void setElementList(ObservableList<BookmarkElement> elementList) {
        mElementList = elementList;
    }
    public static Bookmark getInstance() { return instance; }
    private Bookmark() {}
    public void add(String path) {
        mElementList.add(new BookmarkElement(path));
    }
}
