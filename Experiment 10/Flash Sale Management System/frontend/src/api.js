const API_BASE_URL = '/api';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json'
    },
    ...options
  });

  if (!response.ok) {
    const text = await response.text();
    try {
      const parsed = JSON.parse(text);
      if (parsed.message) {
        throw new Error(parsed.message);
      }
      if (parsed.error) {
        throw new Error(parsed.error);
      }
    } catch {
      throw new Error(text || 'Request failed');
    }
  }

  return response.json();
}

export const api = {
  getSaleEvents: () => request('/sales'),
  createProduct: (payload) => request('/products', { method: 'POST', body: JSON.stringify(payload) }),
  createSaleEvent: (payload) => request('/admin/sales', { method: 'POST', body: JSON.stringify(payload) }),
  joinSale: (saleEventId, payload) => request(`/sales/${saleEventId}/join`, { method: 'POST', body: JSON.stringify(payload) }),
  getOrder: (orderId) => request(`/orders/${orderId}`),
  payOrder: (orderId, payload) => request(`/orders/${orderId}/pay`, { method: 'POST', body: JSON.stringify(payload) })
};
