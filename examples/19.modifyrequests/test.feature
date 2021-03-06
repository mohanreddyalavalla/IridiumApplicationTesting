Feature: Open an application

	# This is where we give readable names to the xpaths, ids, classes, name attributes or
	# css selectors that this test will be interacting with.
	Scenario: Generate Page Object
		Given the alias mappings
			| HomeLink 			| //*[@id="ng-app"]/body/div[1]/div/div/div[1]/div/div[1]/div/a			|

 	# Open up the web page
  	Scenario: Launch App
		And I set the default wait time between steps to "0.2"
		And I set the default wait for elements to be available to "30" seconds
		# Allow all traffic to the main domain
		And I enable the whitelist with responding with "500" for unmatched requests
		And I allow access to the URL regex ".*?dzone.*"
   		# Speed up tests by blocking thumbnails
		And I block access to the URL regex ".*?thumbnail.*" with response "500"
		And I open the application
		And I maximise the window

	Scenario: Navigate the main links
		And I click the link with the text content of "REFCARDZ"
		And I click the link with the text content of "GUIDES"
		And I click the link with the text content of "ZONES"
		And I click the link with the text content of "AGILE"
		And I click the link with the text content of "BIG DATA"
		And I click the link with the text content of "CLOUD"
		And I click the link with the text content of "DATABASE"
		And I click the link with the text content of "DEVOPS"
		And I click the link with the text content of "INTEGRATION"
		And I click the link with the text content of "IOT"
		And I click the link with the text content of "JAVA"
		And I click the link with the text content of "MOBILE"
		And I click the link with the text content of "PERFORMANCE"
		And I click the link with the text content of "WEB DEV"

	Scenario: Open some refcardz
		And I click the link with the text content of "REFCARDZ"
	    # WebDriver considers this link to be obscured by another element, so
	    # we use a special step to click these "hidden" links
		And I click the hidden link with the text content of "REST Practices"
		And I wait "30" seconds for the element found by alias "HomeLink" to be displayed
		And I go back
		And I click the hidden link with the text content of "Understanding CDI"
		And I wait "30" seconds for the element found by alias "HomeLink" to be displayed
		And I go back
