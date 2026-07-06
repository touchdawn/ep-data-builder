import request from './request'

export function createBuild(data) {
  return request.post('/builds', data)
}

export function listBuilds(params) {
  return request.get('/builds', { params })
}

export function getBuild(id) {
  return request.get(`/builds/${id}`)
}
