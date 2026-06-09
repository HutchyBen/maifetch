package maifetch.api;

import java.io.IOException;
import java.util.List;

public final class Pager<T> {
    private Page<T> currentPage;
    private final Models.Mapper<T> mapper;

    Pager(Page<T> currentPage, Models.Mapper<T> mapper) {
        this.currentPage = currentPage;
        this.mapper = mapper;
    }

    public List<T> currentPage() {
        return currentPage.getData();
    }

    public List<T> next() throws IOException {
        if (currentPage.getNext() == null || currentPage.getNext().trim().isEmpty()) {
            throw new PageDoesNotExist();
        }
        currentPage = currentPage.getClient().getPage(currentPage.getNext(), mapper);
        return currentPage.getData();
    }

    public List<T> previous() throws IOException {
        if (currentPage.getPrevious() == null || currentPage.getPrevious().trim().isEmpty()) {
            throw new PageDoesNotExist();
        }
        currentPage = currentPage.getClient().getPage(currentPage.getPrevious(), mapper);
        return currentPage.getData();
    }

    public List<T> first() throws IOException {
        currentPage = currentPage.getClient().getPage(currentPage.getFirst(), mapper);
        return currentPage.getData();
    }

    public List<T> last() throws IOException {
        currentPage = currentPage.getClient().getPage(currentPage.getLast(), mapper);
        return currentPage.getData();
    }

    public Page<T> pageInfo() {
        return currentPage;
    }

    public static final class PageDoesNotExist extends IOException {
        private static final long serialVersionUID = 1L;

        public PageDoesNotExist() {
            super("Page does not exist");
        }
    }
}
