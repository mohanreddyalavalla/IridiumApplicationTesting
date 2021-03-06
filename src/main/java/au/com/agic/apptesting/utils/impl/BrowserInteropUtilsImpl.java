package au.com.agic.apptesting.utils.impl;

import au.com.agic.apptesting.State;
import au.com.agic.apptesting.constants.Constants;
import au.com.agic.apptesting.utils.BrowserDetection;
import au.com.agic.apptesting.utils.BrowserInteropUtils;
import au.com.agic.apptesting.utils.SystemPropertyUtils;
import cucumber.api.java.Before;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of checks required to maintain compatibility between browsers
 */
@Component
public class BrowserInteropUtilsImpl implements BrowserInteropUtils {

	private static final int SLEEP_TIME = 1000;
	private static final Logger LOGGER = LoggerFactory.getLogger(BrowserInteropUtilsImpl.class);

	@Autowired
	private BrowserDetection browserDetection;

	@Autowired
	private SystemPropertyUtils systemPropertyUtils;

	private boolean disableInterop() {
		return systemPropertyUtils.getPropertyAsBoolean(Constants.DISABLE_INTEROP, false);
	}

	@Override
	public boolean treatElementAsHidden(
		@NotNull final WebDriver webDriver,
		@NotNull final WebElement element,
		@NotNull final JavascriptExecutor js) {
		checkNotNull(webDriver);
		checkNotNull(element);
		checkNotNull(js);

		if (systemPropertyUtils.getPropertyAsBoolean(Constants.DISABLE_INTEROP, false)) {
			return false;
		}

		/*
			Note that we check for the presence of a # in the url, and not just that
			the url starts with the # sign, because we'll always get a absolute
			url when we get the href attribute.
		 */
		final boolean isHiddenElement = browserDetection.isPhantomJS(webDriver)
			&& Optional.of(element)
				.filter(e -> "a".equalsIgnoreCase(e.getTagName()))
				.filter(e -> StringUtils.isNotBlank(e.getAttribute("href")))
				.map(e -> StringUtils.trim(e.getAttribute("href")))
				.filter(e -> e.contains("#") || e.startsWith("javascript:"))
				.isPresent();

		/*
			I have noticed that these elements need some additional time before
			they can be clicked.
		 */
		if (isHiddenElement) {
			try {
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException ignored) {
				/*
					Nothing to do here
				 */
			}
		}

		return isHiddenElement;
	}

	@Override
	public void selectFromDropDownList(
		@NotNull final WebDriver webDriver,
		@NotNull final WebElement element,
		@NotNull final String selectElement) {

		checkNotNull(webDriver);
		checkNotNull(element);
		checkNotNull(selectElement);

		final boolean isEdge = browserDetection.isEdge(webDriver);
		final boolean isMarionette = browserDetection.isMarionette(webDriver);
		final boolean isFirefox = browserDetection.isFirefox(webDriver);

		if (!disableInterop() && (isMarionette || isEdge || isFirefox)) {
			LOGGER.info("WEBAPPTESTER-INFO-0010: Detected Edge or Firefox Marionette driver. "
				+ "Applying drop down list selection workaround.");
			/*
				Edge doesn't trigger the change event using the selectByVisibleText() method.
				Firefox Marionette doesn't select anything at all.
				So select the item in javascript and manually trigger the onchange event.
			 */

			final JavascriptExecutor js = (JavascriptExecutor) webDriver;
			js.executeScript(
			"for (var i = 0; i < arguments[0].options.length; i++) {"
				+ "    if (arguments[0].options[i].text === '" + selectElement.replaceAll("'", "\\'") + "') {"
				+ "        arguments[0].selectedIndex = i;"
				+ "        break;"
				+ "    }"
				+ "}"
				+ "if ('createEvent' in document) {"
				+ "    var evt = document.createEvent('HTMLEvents');"
				+ "    evt.initEvent('change', true, true);"
				+ "    arguments[0].dispatchEvent(evt);"
				+ "}"
				+ "else {"
				+ "    arguments[0].fireEvent('onchange');"
				+ "}", element);

		} else {
			final Select select = new Select(element);
			select.selectByVisibleText(selectElement);
		}
	}

	@Override
	public void focusOnElement(@NotNull final WebDriver webDriver, @NotNull final WebElement element) {
		checkNotNull(webDriver);
		checkNotNull(element);

		final boolean isMarionette = browserDetection.isMarionette(webDriver);
		final boolean isFirefox = browserDetection.isFirefox(webDriver);

		final JavascriptExecutor js = (JavascriptExecutor) webDriver;
		js.executeScript("arguments[0].focus();", element);

		if (!disableInterop() && (isMarionette || isFirefox)) {
			LOGGER.info("WEBAPPTESTER-INFO-0010: Detected Firefox Marionette driver. "
				+ "Applying element focus workaround.");

			js.executeScript(
				"arguments[0].focus();"
					+ "if ('createEvent' in document) {"
					+ "    var evt = document.createEvent('HTMLEvents');"
					+ "    evt.initEvent('focus', true, true);"
					+ "    arguments[0].dispatchEvent(evt);"
					+ "}"
					+ "else {"
					+ "    arguments[0].fireEvent('onfocus');"
					+ "}", element);
		} else {
			js.executeScript("arguments[0].focus();", element);
		}
	}

	/**
	 * https://github.com/detro/ghostdriver/issues/20
	 * Replace window.alert and window.confirm for PhantomJS
	 */
	@Before
	public void setup() {
		final WebDriver webDriver = State.THREAD_DESIRED_CAPABILITY_MAP.getWebDriverForThread();
		final boolean isPhantomJS = browserDetection.isPhantomJS(webDriver);

		if (!disableInterop() && isPhantomJS) {
			final JavascriptExecutor js = (JavascriptExecutor) webDriver;
			js.executeScript("window.confirm = function(){return true;}");
			js.executeScript("window.alert = function(){}");
		}
	}

	@Override
	public void waitForAlert(@NotNull WebDriver webDriver, int waitDuration) {
		final boolean isPhantomJS = browserDetection.isPhantomJS(webDriver);

		if (!disableInterop() && isPhantomJS) {
			/*
				This kind of wait is not supported by Phantom JS
			 */
			LOGGER.info("WEBAPPTESTER-INFO-0010: Detected PhantomJS driver."
				+ " Disabling alert wait. This step will always pass, regardless of"
				+ " whether there is an alert or not.");
		} else {
			final WebDriverWait wait = new WebDriverWait(
				webDriver,
				waitDuration,
				Constants.ELEMENT_WAIT_SLEEP_TIMEOUT);
			wait.until(ExpectedConditions.alertIsPresent());
		}
	}

	@Override
	public void acceptAlert(@NotNull WebDriver webDriver) {
		final boolean isPhantomJS = browserDetection.isPhantomJS(webDriver);

		if (!disableInterop() && isPhantomJS) {
			/*
				Do nothing because we have already redefined the alert
				method.
			 */
			LOGGER.info("WEBAPPTESTER-INFO-0010: Detected PhantomJS driver."
				+ " Disabling alert accept. This step will always pass, regardless of"
				+ " whether there is an alert or not.");
		} else {
			final WebDriverWait wait = new WebDriverWait(
				webDriver,
				State.getFeatureStateForThread().getDefaultWait(),
				Constants.ELEMENT_WAIT_SLEEP_TIMEOUT);

			wait.until(ExpectedConditions.alertIsPresent());

			final Alert alert = webDriver.switchTo().alert();
			alert.accept();
		}
	}

	@Override
	public void cancelAlert(@NotNull WebDriver webDriver) {
		final boolean isPhantomJS = browserDetection.isPhantomJS(webDriver);

		if (!disableInterop() && isPhantomJS) {
			/*
				Do nothing because we have already redefined the alert
				method.
			 */
			LOGGER.info("WEBAPPTESTER-INFO-0010: Detected PhantomJS driver."
				+ " Disabling alert dismiss. This step will always pass, regardless of"
				+ " whether there is an alert or not.");
		} else {
			final WebDriverWait wait = new WebDriverWait(
				webDriver,
				State.getFeatureStateForThread().getDefaultWait(),
				Constants.ELEMENT_WAIT_SLEEP_TIMEOUT);

			wait.until(ExpectedConditions.alertIsPresent());

			final Alert alert = webDriver.switchTo().alert();
			alert.dismiss();
		}
	}

}

