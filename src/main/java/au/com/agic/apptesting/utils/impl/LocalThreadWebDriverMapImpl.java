package au.com.agic.apptesting.utils.impl;

import au.com.agic.apptesting.constants.Constants;
import au.com.agic.apptesting.exception.ConfigurationException;
import au.com.agic.apptesting.profiles.configuration.UrlMapping;
import au.com.agic.apptesting.utils.*;
import javaslang.control.Try;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A service that generates local web driver instances to test on the local pc. Assumes that Chrome
 * is present and installed in the default location, and that the webdriver.chrome.driver system
 * property has been set, and is pointing to a version of the driver downloaded from
 * http://chromedriver.storage.googleapis.com/index.html
 */
public class LocalThreadWebDriverMapImpl implements ThreadWebDriverMap {

	private static final SystemPropertyUtils SYSTEM_PROPERTY_UTILS = new SystemPropertyUtilsImpl();
	private static final WebDriverFactory WEB_DRIVER_FACTORY = new WebDriverFactoryImpl();

	/**
	 * The mapping between thread ids and the feature state objects that they use for the tests
	 */
	private final Map<String, FeatureState> threadIdToCapMap = new HashMap<>();

	/**
	 * The mapping between thread ids and the webdrivers that they use for the tests
	 */
	private final Map<String, WebDriver> threadIdToDriverMap = new HashMap<>();

	/**
	 * The index of the Url we are going to be testing
	 */
	private int currentUrl;

	/**
	 * The index of the data set we are going to be testing
	 */
	private int currentDataset;

	/**
	 * The list of URLs associated with the application we are testing
	 */
	private List<UrlMapping> originalApplicationUrls;

	/**
	 * The directory that holds reports and other test script outputs
	 */
	private String reportDirectory;

	/**
	 * The values that can be input into the app
	 */
	private Map<Integer, Map<String, String>> originalDataSets;

	/**
	 * A list of temp folders to delete once the test is finished
	 */
	private List<File> tempFolders;

	/**
	 * The port for the proxy
	 */
	private List<ProxyDetails<?>> proxies;

	@Override
	public void initialise(
			@NotNull final List<DesiredCapabilities> desiredCapabilities,
			@NotNull final List<UrlMapping> applicationUrls,
			@NotNull final Map<Integer, Map<String, String>> datasets,
			@NotNull final String myReportDirectory,
			@NotNull final List<File> myTempFolders,
			@NotNull final List<ProxyDetails<?>> myProxies) {

		checkNotNull(desiredCapabilities);
		checkNotNull(applicationUrls);
		checkNotNull(datasets);
		checkNotNull(myReportDirectory);
		checkNotNull(myTempFolders);
		checkNotNull(myProxies);

		originalApplicationUrls = new ArrayList<>(applicationUrls);
		originalDataSets = new HashMap<>(datasets);
		reportDirectory = myReportDirectory;
		tempFolders = new ArrayList<>(myTempFolders);
		proxies = new ArrayList<>(myProxies);
	}

	@Override
	public synchronized FeatureState getDesiredCapabilitiesForThread(@NotNull final String name) {
		if (threadIdToCapMap.containsKey(name)) {
			return threadIdToCapMap.get(name);
		}

		/*
		  Some validation checking
		*/
		if (originalApplicationUrls.isEmpty()) {
			throw new ConfigurationException(
				"There are no configurations available. "
				+ "Check configuration profiles have the required information in them");
		}

		/*
		  We have allocated our available configurations
		*/
		if (currentUrl >= originalApplicationUrls.size()) {
			throw new ConfigurationException("Configuration pool has been exhausted!");
		}

		/*
		  Get the details that the requesting thread will need
		*/
		final UrlMapping url = originalApplicationUrls.get(currentUrl);

		final Map<String, String> dataSet = originalDataSets.containsKey(currentDataset)
			? new HashMap<>(originalDataSets.get(currentDataset))
			: new HashMap<>();

		/*
			Tick over to the next url when all the capabilities have been consumed
		 */
		++currentDataset;
		if (currentDataset >= getMaxDataSets()) {
			currentDataset = 0;
			++currentUrl;
		}


		final FeatureState featureState = new FeatureStateImpl(
			url,
			dataSet,
			reportDirectory,
			proxies);

		threadIdToCapMap.put(name, featureState);

		return featureState;
	}

	public synchronized WebDriver getWebDriverForThread(@NotNull final String name, final boolean createIfMissing) {
		checkArgument(StringUtils.isNotEmpty(name));

		if (threadIdToDriverMap.containsKey(name)) {
			return threadIdToDriverMap.get(name);
		}

		if (createIfMissing) {

			final WebDriver webDriver = WEB_DRIVER_FACTORY.createWebDriver(proxies);
			threadIdToDriverMap.put(name, webDriver);

			return webDriver;
		}

		return null;
	}

	@Override
	public synchronized void clearWebDriverForThread(@NotNull final String name, final boolean quitDriver) {
		checkArgument(StringUtils.isNotEmpty(name));

		if (threadIdToDriverMap.containsKey(name)) {
			if (quitDriver) {
				threadIdToDriverMap.get(name).quit();
			}
			threadIdToDriverMap.remove(name);
		}
	}

	@Override
	public synchronized int getNumberCapabilities() {
		if (originalApplicationUrls.isEmpty()) {
			throw new ConfigurationException("No application URL specified");
		}
		return originalApplicationUrls.size() * Math.max(getMaxDataSets(), 1);
	}

	@Override
	public List<File> getTempFolders() {
		return tempFolders;
	}

	private Integer getMaxDataSets() {
		try {
			final String maxDataSets =
				SYSTEM_PROPERTY_UTILS.getProperty(Constants.NUMBER_DATA_SETS_SYSTEM_PROPERTY);

			if (StringUtils.isNotBlank(maxDataSets)) {
				final Integer maxDataSetsNumber = Integer.parseInt(
					SYSTEM_PROPERTY_UTILS.getProperty(Constants.NUMBER_DATA_SETS_SYSTEM_PROPERTY));

				return Math.min(originalDataSets.size(), maxDataSetsNumber);
			}
		} catch (final NumberFormatException ignored) {
		  /*
			Input was not a number, so ignore it
		   */
		}

		return originalDataSets.size();
	}

	@Override
	public synchronized void shutdown() {
		for (final WebDriver webdriver : threadIdToDriverMap.values()) {
			try {
				if (!WEB_DRIVER_FACTORY.leaveWindowsOpen()) {
					webdriver.quit();
				}
			} catch (final Exception ignored) {
				// do nothing and continue closing the other webdrivers
			}
		}

        /*
            Clear the map
         */
		threadIdToCapMap.clear();

		/*
			Attemp to delete all the temp folders
		 */
		getTempFolders().stream()
			.forEach(e -> Try.run(() -> FileUtils.deleteDirectory(e)));

        /*
            Reset the list of available configurations
         */
		currentUrl = 0;
	}

	@Override
	public synchronized void shutdown(@NotNull final String name) {
		checkArgument(StringUtils.isNotBlank(name));

		if (threadIdToCapMap.containsKey(name)) {
			this.clearWebDriverForThread(name, !WEB_DRIVER_FACTORY.leaveWindowsOpen());
		}
	}
}
