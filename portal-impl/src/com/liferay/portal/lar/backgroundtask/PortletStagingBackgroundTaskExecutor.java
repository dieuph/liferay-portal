/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.lar.backgroundtask;

import static com.liferay.portal.kernel.lar.lifecycle.ExportImportLifecycleConstants.EVENT_PUBLICATION_PORTLET_LOCAL_FAILED;
import static com.liferay.portal.kernel.lar.lifecycle.ExportImportLifecycleConstants.EVENT_PUBLICATION_PORTLET_LOCAL_STARTED;
import static com.liferay.portal.kernel.lar.lifecycle.ExportImportLifecycleConstants.EVENT_PUBLICATION_PORTLET_LOCAL_SUCCEEDED;
import static com.liferay.portal.kernel.lar.lifecycle.ExportImportLifecycleConstants.PROCESS_FLAG_PORTLET_STAGING_IN_PROCESS;

import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.ExportImportThreadLocal;
import com.liferay.portal.kernel.lar.MissingReferences;
import com.liferay.portal.kernel.lar.lifecycle.ExportImportLifecycleManager;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.BackgroundTask;
import com.liferay.portal.model.ExportImportConfiguration;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.spring.transaction.TransactionHandlerUtil;

import java.io.File;

import java.util.concurrent.Callable;

/**
 * @author Julio Camarero
 * @author Daniel Kocsis
 * @author Akos Thurzo
 */
public class PortletStagingBackgroundTaskExecutor
	extends BaseStagingBackgroundTaskExecutor {

	public PortletStagingBackgroundTaskExecutor() {
		setBackgroundTaskStatusMessageTranslator(
			new PortletStagingBackgroundTaskStatusMessageTranslator());
	}

	@Override
	public BackgroundTaskResult execute(BackgroundTask backgroundTask)
		throws Exception {

		ExportImportConfiguration exportImportConfiguration =
			getExportImportConfiguration(backgroundTask);

		File file = null;
		MissingReferences missingReferences = null;

		try {
			ExportImportThreadLocal.setPortletStagingInProcess(true);

			ExportImportLifecycleManager.fireExportImportLifecycleEvent(
				EVENT_PUBLICATION_PORTLET_LOCAL_STARTED,
				PROCESS_FLAG_PORTLET_STAGING_IN_PROCESS,
				exportImportConfiguration);

			file = LayoutLocalServiceUtil.exportPortletInfoAsFile(
				exportImportConfiguration);

			markBackgroundTask(
				backgroundTask.getBackgroundTaskId(), "exported");

			missingReferences = TransactionHandlerUtil.invoke(
				transactionAttribute,
				new PortletStagingCallable(
					backgroundTask.getBackgroundTaskId(),
					exportImportConfiguration, file));

			ExportImportThreadLocal.setPortletStagingInProcess(false);

			ExportImportLifecycleManager.fireExportImportLifecycleEvent(
				EVENT_PUBLICATION_PORTLET_LOCAL_SUCCEEDED,
				PROCESS_FLAG_PORTLET_STAGING_IN_PROCESS,
				exportImportConfiguration);
		}
		catch (Throwable t) {
			ExportImportThreadLocal.setPortletStagingInProcess(false);

			ExportImportLifecycleManager.fireExportImportLifecycleEvent(
				EVENT_PUBLICATION_PORTLET_LOCAL_FAILED,
				PROCESS_FLAG_PORTLET_STAGING_IN_PROCESS,
				exportImportConfiguration);

			if (_log.isDebugEnabled()) {
				_log.debug(t, t);
			}
			else if (_log.isWarnEnabled()) {
				_log.warn("Unable to publish portlet: " + t.getMessage());
			}

			deleteTempLarOnFailure(file);

			throw new SystemException(t);
		}

		deleteTempLarOnSuccess(file);

		return processMissingReferences(
			backgroundTask.getBackgroundTaskId(), missingReferences);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		PortletStagingBackgroundTaskExecutor.class);

	private class PortletStagingCallable
		implements Callable<MissingReferences> {

		public PortletStagingCallable(
			long backgroundTaskId,
			ExportImportConfiguration exportImportConfiguration, File file) {

			_backgroundTaskId = backgroundTaskId;
			_exportImportConfiguration = exportImportConfiguration;
			_file = file;
		}

		@Override
		public MissingReferences call() throws PortalException {
			LayoutLocalServiceUtil.importPortletDataDeletions(
				_exportImportConfiguration, _file);

			MissingReferences missingReferences =
				LayoutLocalServiceUtil.validateImportPortletInfo(
					_exportImportConfiguration, _file);

			markBackgroundTask(_backgroundTaskId, "validated");

			LayoutLocalServiceUtil.importPortletInfo(
				_exportImportConfiguration, _file);

			return missingReferences;
		}

		private final long _backgroundTaskId;
		private final ExportImportConfiguration _exportImportConfiguration;
		private final File _file;

	}

}