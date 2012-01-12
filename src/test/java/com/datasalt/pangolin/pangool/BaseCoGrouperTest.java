package com.datasalt.pangolin.pangool;

import java.io.IOException;

import com.datasalt.pangolin.grouper.GrouperException;
import com.datasalt.pangolin.grouper.Schema;
import com.datasalt.pangolin.grouper.io.tuple.ITuple;
import com.datasalt.pangolin.grouper.io.tuple.ITuple.InvalidFieldException;
import com.datasalt.pangolin.pangool.SortCriteria.SortOrder;
import com.datasalt.pangolin.pangool.mapreduce.GroupHandler;
import com.datasalt.pangolin.pangool.mapreduce.GroupHandlerWithRollup;
import com.datasalt.pangolin.pangool.mapreduce.InputProcessor;

@SuppressWarnings("rawtypes")
public class BaseCoGrouperTest {

	protected GroupHandler myGroupHandler = new GroupHandler();
	
	protected GroupHandler myGroupHandlerWithRollup = new GroupHandlerWithRollup();

	protected InputProcessor myInputProcessor = new InputProcessor();
	
	protected Sorting getTestSorting() throws InvalidFieldException {
		return new SortingBuilder()
			.add("url", SortOrder.ASC)
			.add("date", SortOrder.DESC)
			.secondarySort(1).add("content", SortOrder.ASC)
			.secondarySort(2).add("name", SortOrder.ASC)
			.buildSorting();
	}
}
