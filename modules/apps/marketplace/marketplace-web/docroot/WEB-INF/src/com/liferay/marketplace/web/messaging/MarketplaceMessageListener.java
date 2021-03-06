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

package com.liferay.marketplace.web.messaging;

import com.liferay.marketplace.model.App;
import com.liferay.marketplace.service.AppLocalService;
import com.liferay.marketplace.service.ModuleLocalService;
import com.liferay.portal.kernel.messaging.BaseMessageListener;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.Properties;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Ryan Park
 * @author Joan Kim
 */
@Component(
	immediate = true, property = {"destination.name=liferay/marketplace"},
	service = MessageListener.class
)
public class MarketplaceMessageListener extends BaseMessageListener {

	@Override
	protected void doReceive(Message message) throws Exception {
		String command = message.getString("command");

		if (!command.equals("deploy")) {
			return;
		}

		Properties properties = PropertiesUtil.load(
			message.getString("properties"));

		long remoteAppId = GetterUtil.getLong(
			properties.getProperty("remote-app-id"));
		String version = properties.getProperty("version");

		if ((remoteAppId <= 0) || Validator.isNull(version)) {
			return;
		}

		String title = properties.getProperty("title");
		String description = properties.getProperty("description");
		String category = properties.getProperty("category");
		String iconURL = properties.getProperty("icon-url");

		App app = _appLocalService.updateApp(
			0, remoteAppId, title, description, category, iconURL, version,
			null);

		String[] bundles = StringUtil.split(properties.getProperty("bundles"));

		for (String bundle : bundles) {
			String[] bundleParts = StringUtil.split(bundle, StringPool.POUND);

			String bundleSymbolicName = bundleParts[0];
			String bundleVersion = bundleParts[1];
			String contextName = bundleParts[2];

			_moduleLocalService.addModule(
				0, app.getAppId(), bundleSymbolicName, bundleVersion,
				contextName);
		}

		String[] contextNames = StringUtil.split(
			properties.getProperty("context-names"));

		for (String contextName : contextNames) {
			_moduleLocalService.addModule(
				0, app.getAppId(), StringPool.BLANK, StringPool.BLANK,
				contextName);
		}

		_appLocalService.processMarketplaceProperties(properties);
	}

	@Reference(unbind = "-")
	protected void setAppLocalService(AppLocalService appLocalService) {
		_appLocalService = appLocalService;
	}

	@Reference(unbind = "-")
	protected void setModuleLocalService(
		ModuleLocalService moduleLocalService) {

		_moduleLocalService = moduleLocalService;
	}

	private AppLocalService _appLocalService;
	private ModuleLocalService _moduleLocalService;

}