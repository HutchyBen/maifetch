package maifetch.api;

import java.util.List;

public final class Page<T> {
    private final MaiTeaClient client;
    private final List<T> data;
    private final String first;
    private final String last;
    private final String previous;
    private final String next;
    private final int currentPage;
    private final int lastPage;
    private final int total;

    Page(
            MaiTeaClient client,
            List<T> data,
            String first,
            String last,
            String previous,
            String next,
            int currentPage,
            int lastPage,
            int total
    ) {
        this.client = client;
        this.data = data;
        this.first = first;
        this.last = last;
        this.previous = previous;
        this.next = next;
        this.currentPage = currentPage;
        this.lastPage = lastPage;
        this.total = total;
    }

    MaiTeaClient getClient() {
        return client;
    }

    public List<T> getData() {
        return data;
    }

    public String getFirst() {
        return first;
    }

    public String getLast() {
        return last;
    }

    public String getPrevious() {
        return previous;
    }

    public String getNext() {
        return next;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    public int getTotal() {
        return total;
    }
}
