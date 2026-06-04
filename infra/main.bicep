targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the azd environment. Used for resource naming and tags.')
param environmentName string

@minLength(1)
@maxLength(90)
@description('Name of the resource group to create or update.')
param resourceGroupName string = 'rg-${environmentName}'

@description('Azure region for the Foundry resources. Instant models are preview-scoped; westus3 is the default.')
param location string = 'westus3'

@description('Object ID of the user or service principal running azd. Used for project RBAC.')
param principalId string

@description('Principal type for the RBAC assignment.')
param principalType string = 'User'

@description('Optional Foundry account name. Leave empty to generate one.')
param aiFoundryAccountName string = ''

@description('Optional Foundry project name. Leave empty to generate one.')
param aiFoundryProjectName string = ''

var tags = {
  'azd-env-name': environmentName
}

resource rg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: resourceGroupName
  location: location
  tags: tags
}

module foundry 'foundry.bicep' = {
  name: 'foundry'
  scope: rg
  params: {
    location: location
    tags: tags
    environmentName: environmentName
    principalId: principalId
    principalType: principalType
    aiFoundryAccountName: aiFoundryAccountName
    aiFoundryProjectName: aiFoundryProjectName
  }
}

output AZURE_RESOURCE_GROUP string = rg.name
output AZURE_LOCATION string = location
output AZURE_AI_ACCOUNT_NAME string = foundry.outputs.accountName
output AZURE_AI_PROJECT_NAME string = foundry.outputs.projectName
output AZURE_AI_ACCOUNT_ID string = foundry.outputs.accountId
output AZURE_AI_PROJECT_ID string = foundry.outputs.projectId
output AZURE_AI_FOUNDRY_PROJECT_ID string = foundry.outputs.projectId
output AZURE_AI_PROJECT_ENDPOINT string = foundry.outputs.projectEndpoint
output FOUNDRY_PROJECT_ENDPOINT string = foundry.outputs.projectEndpoint
output AZURE_OPENAI_ENDPOINT string = foundry.outputs.openAiEndpoint
