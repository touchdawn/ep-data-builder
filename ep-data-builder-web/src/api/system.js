import request from './request'

// 开放令牌
export function listTokens() {
  return request.get('/tokens')
}

export function createToken(data) {
  return request.post('/tokens', data)
}

export function updateToken(id, data) {
  return request.put(`/tokens/${id}`, data)
}

// 用户
export function listUsers() {
  return request.get('/users')
}

export function createUser(data) {
  return request.post('/users', data)
}

export function updateUser(id, data) {
  return request.put(`/users/${id}`, data)
}

// 操作日志
export function listLogs(params) {
  return request.get('/logs', { params })
}
