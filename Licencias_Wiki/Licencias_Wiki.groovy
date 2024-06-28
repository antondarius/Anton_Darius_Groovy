import com.atlassian.confluence.core.Modification
import com.atlassian.confluence.security.login.LoginManager
import com.atlassian.user.GroupManager
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.login.LoginInfo
import com.atlassian.crowd.embedded.api.CrowdService
import com.atlassian.crowd.model.user.TimestampedUser

// Initialize components
PageManager pageManager = ComponentLocator.getComponent(PageManager)
GroupManager groupManager = ComponentLocator.getComponent(GroupManager)
LoginManager loginManager = ComponentLocator.getComponent(LoginManager)
CrowdService crowdService = ComponentLocator.getComponent(CrowdService)

// Get the current date and calculate the cutoff date
Date date = new Date()
def cutDate = date.minus(180)

// Initialize HTML table structure
def html = """
<table style="border-collapse: collapse; width: 30%;">
    <tr>
        <th style="border: 1px solid black; padding: 8px;">Username</th>
        <th style="border: 1px solid black; padding: 8px;"> Last Login</th>
        <th style="border: 1px solid black; padding: 8px;"> Needs a License</th>
    </tr>
"""

// Get the parent page and create a new page for the report
def parentPage = pageManager.getPage(393612960)
Page page = new Page()
def space = parentPage.getSpace()
page.setSpace(space)
page.setTitle("Wiki Unused Licenses " + (date.year + 1900) + "." + (String.format("%02d", (date.month + 1))))  // Date format
page.setParentPage(parentPage)

// Lists to store paid and unused licenses
def paidLicenses = []
def unusedLicenses = []

// Get users in the 'confluence-users' group
def group = groupManager.getGroup('confluence-users')
def users = groupManager.getMemberNames(group).asList()

// Iterate over each user
users.eachWithIndex { user, index ->
    // Pause to avoid overloading the server
    if (index % 500 == 0) {
        Thread.sleep(100)
    }
    
    // Retrieve user information
    def crowdUser = crowdService.getUser(user) as TimestampedUser
    def creationDate = crowdUser?.getCreatedDate()
    LoginInfo loginDetails = loginManager.getLoginInfo(user)
    def lastLogin = (loginDetails) ? loginDetails.getLastSuccessfulLoginDate() : null
    
    // Check if the user is active and was created before the current date
    if (crowdUser?.isActive() && creationDate.time < date.time) {
        paidLicenses.add(user)
        
        // Check if the user has logged in
        if (lastLogin) {
            // Check if the last login was before the cutoff date
            if (lastLogin.before(cutDate)) {
                unusedLicenses.add(user)
                html += """
                <tr>
                    <td style="border: 1px solid black; padding 8px;">${user}</td>
                    <td style="border: 1px solid black; padding 8px;">${lastLogin.format("yyyy-MM-dd")}</td>
                    <td style="border: 1px solid black; padding 8px;"> No </td>
                </tr>
                """
            }
        } else {
            // User has never logged in
            unusedLicenses.add(user)
            html += """
            <tr>
                <td style="border: 1px solid black; padding 8px;">${user}</td>
                <td style="border: 1px solid black; padding 8px;">Never logged in</td>
                <td style="border: 1px solid black; padding 8px;"> Yes </td>
            </tr>
            """
        }
    }
}

// Close the HTML table
html += """
</table>
"""

// Set the content of the new page
page.setBodyAsString(html)
parentPage.addChild(page)

// Save the new page
pageManager.saveContentEntity(page, null, null)

// Update an existing table page with new data
def tablePage = pageManager.getPage(393612968)
def table = tablePage.getBodyAsString()
def newRow = """
    <tr class="">
        <td style="text-align: left;vertical-align: top;">${date.format("MMMM dd yyyy")}</td>
        <td style="text-align: left;vertical-align: top;">3000</td>
        <td style="text-align: left;vertical-align: top;">${paidLicenses.size()}</td>
        <td style="text-align: left;vertical-align: top;">${3000 - paidLicenses.size()}</td>
        <td style="text-align: left;vertical-align: top;">${unusedLicenses.size()}</td>
    </tr>
"""
table = table.replace("</tbody>", newRow + "</tbody>")

// Save the updated table page
pageManager.<Page>saveNewVersion(tablePage, new Modification<Page>() {
    public void modify(Page newPage) {
        tablePage.setBodyAsString(table)
    }
})

// Log the results
log.warn paidLicenses.size()
log.warn unusedLicenses.size()

return html
