package me.grishka.appkit.example.api;

import me.grishka.appkit.api.PaginatedList;

public class SimplePaginatedList<T> extends PaginatedList<T>{

	private int total;

	public SimplePaginatedList(int total){
		this.total=total;
	}

	@Override
	public int total(){
		return total;
	}
}
