export function mockRequest<T>(data: T, delay: number = 300): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(data)
    }, delay)
  })
}

export function mockReject(error: Error, delay: number = 300): Promise<never> {
  return new Promise((_, reject) => {
    setTimeout(() => {
      reject(error)
    }, delay)
  })
}
