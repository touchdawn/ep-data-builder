import request from './request'

export function listFactories(params) {
  return request.get('/factories', { params })
}

export function createFactory(data) {
  return request.post('/factories', data)
}

export function getFactory(id) {
  return request.get(`/factories/${id}`)
}

export function updateFactory(id, data) {
  return request.put(`/factories/${id}`, data)
}

export function deleteFactory(id) {
  return request.delete(`/factories/${id}`)
}
