import request from './request'

// 环境
export function listEnvironments() {
  return request.get('/environments')
}

export function createEnvironment(data) {
  return request.post('/environments', data)
}

export function updateEnvironment(id, data) {
  return request.put(`/environments/${id}`, data)
}

export function deleteEnvironment(id) {
  return request.delete(`/environments/${id}`)
}

// 模块端点
export function listEndpoints(envId) {
  return request.get(`/environments/${envId}/endpoints`)
}

export function createEndpoint(envId, data) {
  return request.post(`/environments/${envId}/endpoints`, data)
}

export function updateEndpoint(id, data) {
  return request.put(`/endpoints/${id}`, data)
}

export function deleteEndpoint(id) {
  return request.delete(`/endpoints/${id}`)
}

// 数据源
export function listDatasources(envId) {
  return request.get(`/environments/${envId}/datasources`)
}

export function createDatasource(envId, data) {
  return request.post(`/environments/${envId}/datasources`, data)
}

export function updateDatasource(id, data) {
  return request.put(`/datasources/${id}`, data)
}

export function deleteDatasource(id) {
  return request.delete(`/datasources/${id}`)
}

export function testDatasourceConnection(id) {
  return request.post(`/datasources/${id}/test-connection`)
}
