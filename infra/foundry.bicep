targetScope = 'resourceGroup'

@description('Azure region for the Foundry resources.')
param location string

@description('Tags to apply to all resources.')
param tags object = {}

@description('Name of the azd environment. Used when generating resource names.')
param environmentName string

@description('Object ID of the user or service principal running azd. Used for project RBAC.')
param principalId string

@description('Principal type for the RBAC assignment.')
param principalType string = 'User'

@description('Optional Foundry account name. Leave empty to generate one.')
param aiFoundryAccountName string = ''

@description('Optional Foundry project name. Leave empty to generate one.')
param aiFoundryProjectName string = ''

var resourceToken = uniqueString(subscription().id, resourceGroup().id, location)
var baseName = toLower(replace(environmentName, '-', ''))
var generatedAccountName = take('${baseName}${resourceToken}', 24)
var accountNameValue = empty(aiFoundryAccountName) ? generatedAccountName : aiFoundryAccountName
var projectNameValue = empty(aiFoundryProjectName) ? take('${environmentName}-project', 64) : aiFoundryProjectName
var azureAiUserRoleDefinitionId = '53ca6127-db72-4b80-b1b0-d745d6d5456d'

resource account 'Microsoft.CognitiveServices/accounts@2025-06-01' = {
  name: accountNameValue
  location: location
  tags: tags
  sku: {
    name: 'S0'
  }
  kind: 'AIServices'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    allowProjectManagement: true
    customSubDomainName: accountNameValue
    disableLocalAuth: true
    networkAcls: {
      defaultAction: 'Allow'
      virtualNetworkRules: []
      ipRules: []
    }
    publicNetworkAccess: 'Enabled'
  }

  resource project 'projects' = {
    name: projectNameValue
    location: location
    identity: {
      type: 'SystemAssigned'
    }
    properties: {
      description: 'Instant models Java sample project'
      displayName: projectNameValue
    }
  }
}

resource localUserAzureAiUserRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(principalId)) {
  scope: account::project
  name: guid(account::project.id, principalId, azureAiUserRoleDefinitionId)
  properties: {
    principalId: principalId
    principalType: principalType
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', azureAiUserRoleDefinitionId)
  }
}

output accountName string = account.name
output projectName string = account::project.name
output accountId string = account.id
output projectId string = account::project.id
output projectEndpoint string = account::project.properties.endpoints['AI Foundry API']
output openAiEndpoint string = account.properties.endpoints['OpenAI Language Model Instance API']
