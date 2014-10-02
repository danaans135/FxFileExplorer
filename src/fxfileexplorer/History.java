package fxfileexplorer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class History {
    private List<Path> pathList = new ArrayList<>();
    private int pathListIndex = -1;

    public void add(Path path) {
        if (pathListIndex < pathList.size() - 1) {
            pathList.subList(pathListIndex + 1, pathList.size()).clear();
        }
        if (pathListIndex < 0 || !pathList.get(pathListIndex).equals(path)) {
            pathList.add(path);
            pathListIndex = pathList.size() - 1;
        }
    }

    public Path back() {
        if (pathListIndex > 0) {
            pathListIndex--;
            return pathList.get(pathListIndex);
        } else {
            return null;
        }
    }

    public Path next() {
        if (pathListIndex < pathList.size() - 1) {
            pathListIndex++;
            return pathList.get(pathListIndex);
        } else {
            return null;
        }
    }

}
