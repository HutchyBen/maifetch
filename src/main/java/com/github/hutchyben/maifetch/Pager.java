package com.github.hutchyben.maifetch;

import java.io.IOException;
import java.lang.reflect.Type;

public final class Pager<T> {
    private final MaiteaClient client;
    private final Type pageType;
    private MaiteaModels.Page<T> currentPage;

    Pager(MaiteaClient client, Type pageType, MaiteaModels.Page<T> currentPage) {
        this.client = client;
        this.pageType = pageType;
        this.currentPage = currentPage;
    }

    public T currentPage() {
        return currentPage.data;
    }

    public T next() throws IOException {
        if (currentPage.links == null || currentPage.links.next == null) {
            throw new PageDoesNotExistException();
        }
        currentPage = client.getPage(currentPage.links.next, pageType);
        return currentPage.data;
    }

    public T prev() throws IOException {
        if (currentPage.links == null || currentPage.links.prev == null) {
            throw new PageDoesNotExistException();
        }
        currentPage = client.getPage(currentPage.links.prev, pageType);
        return currentPage.data;
    }

    public T first() throws IOException {
        if (currentPage.links == null || currentPage.links.first == null) {
            throw new PageDoesNotExistException();
        }
        currentPage = client.getPage(currentPage.links.first, pageType);
        return currentPage.data;
    }

    public T last() throws IOException {
        if (currentPage.links == null || currentPage.links.last == null) {
            throw new PageDoesNotExistException();
        }
        currentPage = client.getPage(currentPage.links.last, pageType);
        return currentPage.data;
    }

    public static final class PageDoesNotExistException extends IOException {
        private static final long serialVersionUID = 1L;

        PageDoesNotExistException() {
            super("Page does not exist");
        }
    }
}
