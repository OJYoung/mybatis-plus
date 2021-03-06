/**
 * Copyright (c) 2011-2020, hubin (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.baomidou.mybatisplus.plugins;

import com.baomidou.mybatisplus.entity.CountOptimize;
import com.baomidou.mybatisplus.plugins.pagination.DialectFactory;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;
import com.baomidou.mybatisplus.toolkit.PluginUtils;
import com.baomidou.mybatisplus.toolkit.SqlUtils;
import com.baomidou.mybatisplus.toolkit.StringUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.Properties;

/**
 * <p>
 * 缓存分页拦截器
 * </p>
 *
 * @author hubin
 * @Date 2016-01-23
 */
@Intercepts({
		@Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class, RowBounds.class,
				ResultHandler.class }),
		@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
public class CachePaginationInterceptor extends PaginationInterceptor implements Interceptor {

	private static final Log logger = LogFactory.getLog(CachePaginationInterceptor.class);

	/* 溢出总页数，设置第一页 */
	private boolean overflowCurrent = false;
	/* Count优化方式 */
	private String optimizeType = "default";
	/* 方言类型 */
	private String dialectType;
	/* 方言实现类 */
	private String dialectClazz;

	/**
	 * Physical Pagination Interceptor for all the queries with parameter
	 * {@link org.apache.ibatis.session.RowBounds}
	 */
	public Object intercept(Invocation invocation) throws Throwable {

		Object target = invocation.getTarget();
		if (target instanceof StatementHandler) {
			StatementHandler statementHandler = (StatementHandler) PluginUtils.realTarget(invocation.getTarget());
			MetaObject metaStatementHandler = SystemMetaObject.forObject(statementHandler);
			RowBounds rowBounds = (RowBounds) metaStatementHandler.getValue("delegate.rowBounds");

			if (rowBounds == null || rowBounds == RowBounds.DEFAULT) {
				return invocation.proceed();
			}
			BoundSql boundSql = (BoundSql) metaStatementHandler.getValue("delegate.boundSql");
			String originalSql = (String) boundSql.getSql();

			if (rowBounds instanceof Pagination) {
				Pagination page = (Pagination) rowBounds;
				boolean orderBy = true;
				if (page.isSearchCount()) {
					CountOptimize countOptimize = SqlUtils.getCountOptimize(originalSql, optimizeType, dialectType,
							page.isOptimizeCount());
					orderBy = countOptimize.isOrderBy();
				}
				String buildSql = SqlUtils.concatOrderBy(originalSql, page, orderBy);
				originalSql = DialectFactory.buildPaginationSql(page, buildSql, dialectType, dialectClazz);
			} else {
				// support physical Pagination for RowBounds
				originalSql = DialectFactory.buildPaginationSql(rowBounds, originalSql, dialectType, dialectClazz);
			}

			metaStatementHandler.setValue("delegate.boundSql.sql", originalSql);
			metaStatementHandler.setValue("delegate.rowBounds.offset", RowBounds.NO_ROW_OFFSET);
			metaStatementHandler.setValue("delegate.rowBounds.limit", RowBounds.NO_ROW_LIMIT);
		} else {
			MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
			Object parameterObject = invocation.getArgs()[1];
			RowBounds rowBounds = (RowBounds) invocation.getArgs()[2];
			if (rowBounds == null || rowBounds == RowBounds.DEFAULT) {
				return invocation.proceed();
			}

			BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
			String originalSql = (String) boundSql.getSql();

			if (rowBounds instanceof Pagination) {
				Pagination page = (Pagination) rowBounds;
				if (page.isSearchCount()) {
					CountOptimize countOptimize = SqlUtils.getCountOptimize(originalSql, optimizeType, dialectType,
							page.isOptimizeCount());
					super.count(countOptimize.getCountSQL(), mappedStatement, boundSql, page);
					if (page.getTotal() <= 0) {
						return invocation.proceed();
					}
				}
			}
		}
		return invocation.proceed();

	}

	public Object plugin(Object target) {
		if (target instanceof Executor) {
			return Plugin.wrap(target, this);
		}
		if (target instanceof StatementHandler) {
			return Plugin.wrap(target, this);
		}
		return target;
	}

	public void setProperties(Properties prop) {
		String dialectType = prop.getProperty("dialectType");
		String dialectClazz = prop.getProperty("dialectClazz");
		if (StringUtils.isNotEmpty(dialectType)) {
			this.dialectType = dialectType;
		}
		if (StringUtils.isNotEmpty(dialectClazz)) {
			this.dialectClazz = dialectClazz;
		}
	}

	public void setDialectType(String dialectType) {
		this.dialectType = dialectType;
	}

	public void setDialectClazz(String dialectClazz) {
		this.dialectClazz = dialectClazz;
	}

	public void setOverflowCurrent(boolean overflowCurrent) {
		this.overflowCurrent = overflowCurrent;
	}

	public void setOptimizeType(String optimizeType) {
		this.optimizeType = optimizeType;
	}
}
