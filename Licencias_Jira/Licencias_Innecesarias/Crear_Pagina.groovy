import java.lang.Exception
import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.ApplicationLinkService
import com.atlassian.applinks.api.application.confluence.ConfluenceApplicationType
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.sal.api.net.Request
import com.atlassian.sal.api.net.Response
import com.atlassian.sal.api.net.ResponseException
import com.atlassian.sal.api.net.ResponseHandler
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.component.ComponentAccessor
import groovyx.net.http.RESTClient
import com.atlassian.jira.security.login.LoginManager

// Initialize managers
def groupManager = ComponentAccessor.getGroupManager()
def userManager = ComponentAccessor.getUserManager()
def loginManager = ComponentAccessor.getComponent(LoginManager)

// Retrieve users in the 'jira-users' group
def users = groupManager.getUsersInGroup('jira-users')

// Lists to store paid and unused licenses
def paidLicenses = []
def unusedLicenses = []

// HTML table header for the report
def html = """
<table style="border-collapse: collapse; width: 30%;">
    <tr>
        <th style="border: 1px solid black; padding: 8px;">Username</th>
        <th style="border: 1px solid black; padding: 8px;"> Last Login</th>
        <th style="border: 1px solid black; padding: 8px;"> Needs a License </th>
    </tr>
"""

// Calculate the cutoff date for determining inactive users
Date date = new Date()
def cutDate = date.minus(180)

// Iterate over each user in the 'jira-users' group
users.eachWithIndex { user, index  ->
    // Pause to avoid overloading the server
    if(index % 500 == 0){
        Thread.sleep(100)
    }
    
    // Retrieve login information for the user
    def loginInfo = loginManager.getLoginInfo(user.username)
    def lastLogin = loginInfo?.getLastLoginTime()
    def loginDate = (lastLogin) ? new Date(lastLogin).format("yyyy-MM-dd") : null
    
    // Check if the user is active and was created before the current date
    if(user.isActive() && user.createdDate < date) {
        paidLicenses.add(user)
        
        // Check if the user has logged in
        if(lastLogin) {
            // Check if the last login was before the cutoff date
            if(lastLogin < cutDate.time) {
                unusedLicenses.add(user)
                html += """
                <tr>
                    <td style="border: 1px solid black; padding 8px;">${user.username}</td>
                    <td style="border: 1px solid black; padding 8px;">${loginDate}</td>
                    <td style="border: 1px solid black; padding 8px;"> No </td>
                </tr>
                """
            }
        } else {
            // User has never logged in
            unusedLicenses.add(user)
            html += """
            <tr>
                <td style="border: 1px solid black; padding 8px;">${user.username}</td>
                <td style="border: 1px solid black; padding 8px;">${loginDate}</td>
                <td style="border: 1px solid black; padding 8px;"> No </td>
            </tr>
            """
        }
    }
}

// Close the HTML table
html += """
</table>
"""

// Method to get the primary Confluence application link
static ApplicationLink getPrimaryConfluenceLink() {
    def applicationLinkService = ComponentLocator.getComponent(ApplicationLinkService)
    final ApplicationLink conflLink = applicationLinkService.getPrimaryApplicationLink(ConfluenceApplicationType)
    conflLink
}

// Retrieve the primary Confluence application link
def confluenceLink = getPrimaryConfluenceLink()
assert confluenceLink

// Create a request factory for authenticated requests
def authenticatedRequestFactory = confluenceLink.createImpersonatingAuthenticatedRequestFactory()

// Set the page title for the report
def pageTitle = "Wiki Unused Licenses " + (date.year + 1900) + "." + (String.format("%02d", (date.month + 1)))

// Parameters for the Confluence page creation
def params = [
    type : "page",
    title : pageTitle,
    space: [
        key: "TD" // Change to productive space key
    ],
    ancestors : [
        [
            type : "page",
            id : "382272259" // Change to productive page ID
        ]
    ],
    body : [
        storage : [
            value : html,
            representation : "storage"
        ]
    ]
]

// Send the request to create the Confluence page
authenticatedRequestFactory
    .createRequest(Request.MethodType.POST,"rest/api/content")
    .addHeader("Content-Type", "application/json")
    .setRequestBody(new JsonBuilder(params).toString())
    .execute(new ResponseHandler<Response>() {
        @Override
        void handle(Response response) throws ResponseException {
            // Check the response status
            if(response.statusCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(response.getResponseBodyAsString())
            } else {
                // Parse the response to get the web URL of the created page
                def webUrl = new JsonSlurper().parseText(response.responseBodyAsString)["_links"]["webui"]
            }
        }
    })
