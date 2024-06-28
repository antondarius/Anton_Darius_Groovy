import java.net.HttpURLConnection
import org.apache.http.entity.ContentType
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
import static groovyx.net.http.ContentType.JSON

// Retrieves the primary Confluence application link
static ApplicationLink getPrimaryConfluenceLink() {
    def applicationLinkService = ComponentLocator.getComponent(ApplicationLinkService)
    final ApplicationLink conflLink = applicationLinkService.getPrimaryApplicationLink(ConfluenceApplicationType)
    conflLink
}

// Access Jira components
def groupManager = ComponentAccessor.getGroupManager()
def userManager = ComponentAccessor.getUserManager()
def loginManager = ComponentAccessor.getComponent(LoginManager)

// Retrieve all users in the 'jira-users' group
def users = groupManager.getUsersInGroup('jira-users')

// Calculate the cut-off date (180 days ago)
Date date = new Date()
def cutDate = date.minus(180)

// Lists to store users with paid and unused licenses
def paidLicenses = []
def unusedLicenses = []

// Iterate through each user to check their activity status and last login time
users.eachWithIndex { user, index ->
    if (index % 100 == 0) {
        Thread.sleep(100) // Throttle to avoid overloading the server
    }
    def loginInfo = loginManager.getLoginInfo(user.username)
    def lastLogin = loginInfo.getLastLoginTime()
    if (user.isActive()) {
        paidLicenses.add(user)
        if (lastLogin) {
            if (lastLogin < cutDate.time) {
                unusedLicenses.add(user) // Add to unused if last login was before the cut-off date
            }
        } else {
            unusedLicenses.add(user) // Add to unused if never logged in
        }
    }
}

// HTML row template to add to the Confluence page
def newRow = """
<tr class="">
    <td style="text-align: left;vertical-align: top;">${date.format("MMMM dd yyyy")}</td>
    <td style="text-align: left;vertical-align: top;">3000</td>
    <td style="text-align: left;vertical-align: top;">${paidLicenses.size()}</td>
    <td style="text-align: left;vertical-align: top;">${3000 - (paidLicenses.size())}</td>
    <td style="text-align: left;vertical-align: top;">${unusedLicenses.size()}</td>
</tr>
"""

// Variables to store Confluence page data
def pageVersion
String pageBody

// Get the primary Confluence application link
def confluenceLink = getPrimaryConfluenceLink()
def authenticatedRequestFactory = confluenceLink.createImpersonatingAuthenticatedRequestFactory()

// Fetch the current version of the Confluence page
authenticatedRequestFactory
    .createRequest(Request.MethodType.GET, "rest/api/content/393612520?expand=version")
    .execute(new ResponseHandler<Response>() {
        @Override
        void handle(Response response) throws ResponseException {
            if (response.statusCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(response.getResponseBodyAsString())
            } else {
                pageVersion = new JsonSlurper().parseText(response.responseBodyAsString)["version"]["number"]
            }
        }
    })

// Fetch the current body of the Confluence page
authenticatedRequestFactory
    .createRequest(Request.MethodType.GET, "rest/api/content/393612520?expand=body.storage")
    .execute(new ResponseHandler<Response>() {
        @Override
        void handle(Response response) throws ResponseException {
            if (response.statusCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(response.getResponseBodyAsString())
            } else {
                pageBody = new JsonSlurper().parseText(response.responseBodyAsString)["body"]["storage"]["value"]
            }
        }
    })

// Update the page body with the new row
pageBody = pageBody.replace("</tbody>", newRow + "</tbody>")

// Prepare the request payload to update the Confluence page
def params = [
    type: "page",
    title: "Jira Table per Month",
    space: [
        key: "JW"
    ],
    body: [
        storage: [
            value: pageBody,
            representation: "storage"
        ]
    ],
    version: [
        number: ((pageVersion as int) + 1)
    ]
]

// Update the Confluence page with the new content
authenticatedRequestFactory
    .createRequest(Request.MethodType.PUT, "rest/api/content/393612520?expand=body.storage")
    .addHeader("Content-Type", "application/json")
    .setRequestBody(new JsonBuilder(params).toString())
    .execute(new ResponseHandler<Response>() {
        @Override
        void handle(Response response) throws ResponseException {
            if (response.statusCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(response.getResponseBodyAsString())
            } else {
                pageBody = new JsonSlurper().parseText(response.responseBodyAsString)["body"]["storage"]["value"]
            }
        }
    })

// Return the updated page body
return pageBody
