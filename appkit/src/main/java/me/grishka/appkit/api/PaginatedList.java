package me.grishka.appkit.api;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by grishka on 16.06.15.
 */
public abstract class PaginatedList<T> extends ArrayList<T>{

	public PaginatedList(int capacity) {
		super(capacity);
	}

	public PaginatedList() {
	}

	public PaginatedList(Collection<? extends T> collection) {
		super(collection);
	}

	public abstract int total();
}
