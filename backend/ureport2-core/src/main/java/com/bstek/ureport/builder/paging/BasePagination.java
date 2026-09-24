/*******************************************************************************
 * Copyright 2017 Bstek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.bstek.ureport.builder.paging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bstek.ureport.builder.Context;
import com.bstek.ureport.definition.Band;
import com.bstek.ureport.definition.HeaderFooterDefinition;
import com.bstek.ureport.model.Cell;
import com.bstek.ureport.model.Column;
import com.bstek.ureport.model.Report;
import com.bstek.ureport.model.Row;


/**
 * @author Jacky.gao
 * @since 2017年1月17日
 */
public abstract class BasePagination {
	protected void buildSummaryRows(List<Row> summaryRows,List<Page> pages){
		Page lastPage=pages.get(pages.size()-1);
		List<Row> lastPageRows=lastPage.getRows();
		for(Row row:summaryRows){
			row.setPageIndex(pages.size());
			lastPageRows.add(row);
		}
	}
	protected Page buildPage(List<Row> rows,List<Row> headerRows,List<Row> footerRows,List<Row> titleRows,int pageIndex,Report report){
		int rowSize=rows.size();
		Row lastRow=rows.get(rowSize-1);
		int lastRowNumber=lastRow.getRowNumber();
		List<Column> columns=report.getColumns();
		Context context=report.getContext();
		context.setPageIndex(pageIndex);
		context.setCurrentPageRows(pageIndex,rows);
		Map<Row, Map<Column, Cell>> rowColCellsMap=report.getRowColCellMap();
		List<Row> reportRows=report.getRows();
		for(int i=0;i<rows.size();i++){
			Row row=rows.get(i);
			Map<Column,Cell> colMap=rowColCellsMap.get(row);
			if(colMap==null){
				continue;
			}
			for(Column col:columns){
				Cell cell=colMap.get(col);
				if(cell==null){
					continue;
				}
				int rowSpan=cell.getPageRowSpan();
				if(rowSpan==0){
					continue;
				}else{
					int span=rowSpan-1;
					int pageRowNumber=i+1;
					int maxRow=pageRowNumber+span;
					if(maxRow<=rowSize){
						continue;
					}else{
						Cell newCell=cell.newCell();
						newCell.setFormatData(cell.getFormatData());
						newCell.setForPaging(true);
						int leftSpan=rowSize-pageRowNumber;
						if(leftSpan>0){
							leftSpan++;							
							cell.setPageRowSpan(leftSpan);
						}else{
							cell.setPageRowSpan(0);
						}
						newCell.setData(cell.getData());
						int newSpan=maxRow-rowSize;
						if(newSpan>1){
							newCell.setPageRowSpan(newSpan);
							newCell.setRowSpan(newSpan);
						}else{
							newCell.setPageRowSpan(0);		
							newCell.setRowSpan(0);
						}
						
						int nextRowNumber=lastRowNumber+1;
						Row nextRow=fetchNextRow(reportRows, nextRowNumber-1);
						newCell.setRow(nextRow);
						Map<Column,Cell> cmap=null;
						if(rowColCellsMap.containsKey(nextRow)){
							cmap=rowColCellsMap.get(nextRow);
						}else{
							cmap=new HashMap<Column,Cell>();
							rowColCellsMap.put(nextRow, cmap);
						}
						cmap.put(newCell.getColumn(),newCell);
					}
				}
			}
		}
		int headerRowSize=headerRows.size()-1;
		for(int i=headerRowSize;i>-1;i--){
			Row row=headerRows.get(i);
			row.setPageIndex(pageIndex);
			Row newRow=duplicateRepeateRow(row, context);
			newRow.setPageIndex(pageIndex);
			rows.add(0,newRow);
			Map<Column,Cell> colMap=rowColCellsMap.get(newRow);
			if(colMap==null){
				continue;
			}
			for(Column col:columns){
				Cell cell=colMap.get(col);
				if(cell==null){
					continue;
				}
			}
		}
		if(pageIndex==1){
			int titleRowSize=titleRows.size()-1;
			for(int i=titleRowSize;i>-1;i--){
				Row row=titleRows.get(i);
				rows.add(0,row);
				Map<Column,Cell> colMap=rowColCellsMap.get(row);
				if(colMap==null){
					continue;
				}
				for(Column col:columns){
					Cell cell=colMap.get(col);
					if(cell==null){
						continue;
					}
				}
			}
		}
		for(Row row:footerRows){
			Row newRow=duplicateRepeateRow(row, context);
			newRow.setPageIndex(pageIndex);
			rows.add(newRow);
			Map<Column,Cell> colMap=rowColCellsMap.get(newRow);
			if(colMap==null){
				continue;
			}
			for(Column col:columns){
				Cell cell=colMap.get(col);
				if(cell==null){
					continue;
				}
			}
		}
		Page page=new Page(rows,columns);
		return page;
	}
	
	private Row fetchNextRow(List<Row> reportRows,int rowNumber){
		Row row=null;
		do{
			if(rowNumber>=reportRows.size()){
				break;
			}
			row=reportRows.get(rowNumber);
			rowNumber++;
		}while(row.getRealHeight()==0);
		return row;
	}

	private Row duplicateRepeateRow(Row row,Context context){
		Row newRow=row.newRow();
		newRow.setPageIndex(row.getPageIndex());
		Map<Row, Map<Column, Cell>> cellMap=context.getReport().getRowColCellMap();
		Map<Column, Cell> map=cellMap.get(row);
		if(map==null){
			return newRow;
		}
		Map<Column, Cell> newMap=new HashMap<Column,Cell>();
		cellMap.put(newRow, newMap);
		List<Column> columns=context.getReport().getColumns();
		for(Column col:columns){
			Cell cell=map.get(col);
			if(cell==null){
				continue;
			}
			Cell newCell=cell.newCell();
			newCell.setRow(newRow);
			newCell.setFormatData(cell.getFormatData());
			newCell.setData(cell.getData());
			newCell.setCustomCellStyle(cell.getCustomCellStyle());
			newCell.setFormatData(cell.getFormatData());
			newCell.setExistPageFunction(cell.isExistPageFunction());
			if(cell.isExistPageFunction()){
				context.addExistPageFunctionCells(newCell);				
			}
			newMap.put(col, newCell);
		}
		return newRow;
	}

	/**
	 * 防止最后一页出现孤行（widow/orphan control）。
	 * <p>
	 * 问题：FitPagePagination/FixRowsPagination 都按「凑够一页就开新页」分页，
	 * 报告末尾常常只剩 1~2 行被独自挤到最后一页，剩下的页面空间几乎全空，
	 * 既浪费纸张、视觉上也很突兀（典型 widow）。
	 * <p>
	 * 策略：循环检查末页「真正的内容行数」（按 band 过滤掉标题/重复表头/重复表尾），
	 * 少于阈值就把末页的全部内容行搬回到上一页中（插在该页表尾重复行的前面），
	 * 然后删掉末页；继续往前推，直到末页够满或者只剩一页（只剩一页就没法合并，
	 * 宁可单页也不删内容）。阈值 3 是常见 widow 控制默认值。
	 * <p>
	 * 注意：
	 * <ul>
	 *   <li>本方法只动 pages 列表，不写回 Context.totalPages，调用方负责在调用前后各设置一次</li>
	 *   <li>合并后行 row.pageIndex 会更新为新末页的索引，pdf/word 渲染取 row.pageIndex 算页码</li>
	 *   <li>buildSummaryRows 必须在调用本方法之后执行，否则汇总行可能被合并掉</li>
	 * </ul>
	 */
	protected void preventLastPageOrphan(List<Page> pages, int minContentRows) {
		while (pages.size() > 1) {
			Page lastPage = pages.get(pages.size() - 1);
			List<Row> lastRows = lastPage.getRows();
			int contentCount = 0;
			for (int i = 0; i < lastRows.size(); i++) {
				Row r = lastRows.get(i);
				if (r.getBand() == null) {
					contentCount++;
				}
			}
			if (contentCount >= minContentRows) {
				return;
			}
			if (contentCount == 0) {
				// 末页只有 headerrepeat/footerrepeat/title，没真实数据行；
				// 直接删掉，避免单独一页空白
				pages.remove(pages.size() - 1);
				continue;
			}
			Page prevPage = pages.get(pages.size() - 2);
			List<Row> prevRows = prevPage.getRows();
			// 上一页末尾若挂有 footerrepeat 行，新内容要插到它们之前，
			// 否则表格内容会跑到页脚下面、被遮挡
			int insertIdx = prevRows.size();
			while (insertIdx > 0 && prevRows.get(insertIdx - 1).getBand() == Band.footerrepeat) {
				insertIdx--;
			}
			int newPageIndex = prevRows.isEmpty() ? 1 : prevRows.get(0).getPageIndex();
			List<Row> toMove = new ArrayList<Row>();
			for (int i = 0; i < lastRows.size(); i++) {
				Row r = lastRows.get(i);
				if (r.getBand() == null) {
					r.setPageIndex(newPageIndex);
					toMove.add(r);
				}
			}
			prevRows.addAll(insertIdx, toMove);
			pages.remove(pages.size() - 1);
		}
	}

	protected void buildPageHeaderFooter(List<Page> pages,Report report){
		int totalPages=pages.size();
		for(int i=0;i<totalPages;i++){
			Page page=pages.get(i);
			HeaderFooterDefinition headerDef=report.getHeader();
			int pageIndex=i+1;
			if(headerDef!=null){
				HeaderFooter hf=headerDef.buildHeaderFooter(pageIndex, report.getContext());
				page.setHeader(hf);
			}
			HeaderFooterDefinition footerDef=report.getFooter();
			if(footerDef!=null){
				HeaderFooter hf=footerDef.buildHeaderFooter(pageIndex, report.getContext());
				page.setFooter(hf);
			}
		}
	}
}
