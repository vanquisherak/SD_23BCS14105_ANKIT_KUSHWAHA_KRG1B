import { useEffect, useMemo, useState } from 'react';
import { api } from './api';

const initialProduct = {
  sku: '',
  name: '',
  totalStock: 0,
  regularPrice: 0
};

const initialSale = {
  productId: '',
  startTime: '',
  endTime: '',
  saleStock: 0,
  maxPerUser: 1,
  salePrice: 0
};

const initialPurchase = {
  userId: '',
  quantity: 1
};

function App() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [productForm, setProductForm] = useState(initialProduct);
  const [saleForm, setSaleForm] = useState(initialSale);
  const [purchaseForms, setPurchaseForms] = useState({});
  const [orders, setOrders] = useState({});

  const liveCount = useMemo(() => events.filter((e) => e.status === 'LIVE').length, [events]);

  async function loadEvents() {
    setLoading(true);
    setError('');
    try {
      const data = await api.getSaleEvents();
      setEvents(data);
    } catch (err) {
      setError(err.message || 'Failed to load sale events');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadEvents();
  }, []);

  async function handleCreateProduct(event) {
    event.preventDefault();
    setError('');
    setSuccess('');

    try {
      await api.createProduct({
        ...productForm,
        totalStock: Number(productForm.totalStock),
        regularPrice: Number(productForm.regularPrice)
      });
      setSuccess('Product created. Use its generated product ID to create a sale event.');
      setProductForm(initialProduct);
      await loadEvents();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleCreateSale(event) {
    event.preventDefault();
    setError('');
    setSuccess('');

    try {
      await api.createSaleEvent({
        ...saleForm,
        productId: Number(saleForm.productId),
        saleStock: Number(saleForm.saleStock),
        maxPerUser: Number(saleForm.maxPerUser),
        salePrice: Number(saleForm.salePrice)
      });
      setSuccess('Sale event created successfully.');
      setSaleForm(initialSale);
      await loadEvents();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleJoinSale(event, saleEventId) {
    event.preventDefault();
    setError('');
    setSuccess('');

    const form = purchaseForms[saleEventId] || initialPurchase;

    try {
      const result = await api.joinSale(saleEventId, {
        userId: Number(form.userId),
        quantity: Number(form.quantity),
        idempotencyKey: `${saleEventId}-${form.userId}`
      });

      if (result.error) {
        if (result.error === 'SOLD_OUT') {
          setError(`Sold out. Waitlist position: ${result.waitlistPosition}`);
        } else if (result.error === 'TOO_MANY_REQUESTS') {
          setError(`Too many requests. Retry after ${result.retryAfter} seconds.`);
        } else {
          setError(result.error);
        }
        return;
      }

      setSuccess(`Slot reserved. Order ID: ${result.orderId}`);
      setPurchaseForms((prev) => ({
        ...prev,
        [saleEventId]: initialPurchase
      }));
      setOrders((prev) => ({
        ...prev,
        [saleEventId]: {
          orderId: result.orderId,
          userId: Number(form.userId),
          status: 'QUEUED',
          expiresAt: result.expiresAt
        }
      }));
      await loadEvents();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleRefreshOrder(saleEventId) {
    const order = orders[saleEventId];
    if (!order?.orderId) {
      return;
    }

    setError('');
    try {
      const response = await api.getOrder(order.orderId);
      setOrders((prev) => ({
        ...prev,
        [saleEventId]: {
          ...prev[saleEventId],
          status: response.status,
          expiresAt: response.expiresAt
        }
      }));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handlePayOrder(saleEventId) {
    const order = orders[saleEventId];
    if (!order?.orderId) {
      return;
    }

    setError('');
    setSuccess('');
    try {
      const result = await api.payOrder(order.orderId, {
        userId: Number(order.userId),
        paymentReference: `txn-${Date.now()}`
      });
      setOrders((prev) => ({
        ...prev,
        [saleEventId]: {
          ...prev[saleEventId],
          status: result.status
        }
      }));
      setSuccess(`Payment completed for order ${result.orderId}`);
      await loadEvents();
    } catch (err) {
      setError(err.message);
    }
  }

  function onPurchaseInputChange(saleEventId, field, value) {
    setPurchaseForms((prev) => ({
      ...prev,
      [saleEventId]: {
        ...(prev[saleEventId] || initialPurchase),
        [field]: value
      }
    }));
  }

  return (
    <div className="page">
      <header className="hero">
        <h1>Flash Sale Management System</h1>
        <div className="metrics">
          <div>
            <span>{events.length}</span>
            <p>Total Events</p>
          </div>
          <div>
            <span>{liveCount}</span>
            <p>Live Events</p>
          </div>
        </div>
      </header>

      <main className="grid">
        <section className="card">
          <h2>Create Product</h2>
          <form onSubmit={handleCreateProduct}>
            <input placeholder="SKU" value={productForm.sku} onChange={(e) => setProductForm({ ...productForm, sku: e.target.value })} required />
            <input placeholder="Name" value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} required />
            <input type="number" min="0" placeholder="Total Stock" value={productForm.totalStock} onChange={(e) => setProductForm({ ...productForm, totalStock: e.target.value })} required />
            <input type="number" min="0" step="0.01" placeholder="Regular Price" value={productForm.regularPrice} onChange={(e) => setProductForm({ ...productForm, regularPrice: e.target.value })} required />
            <button type="submit">Save Product</button>
          </form>
        </section>

        <section className="card">
          <h2>Create Sale Event</h2>
          <form onSubmit={handleCreateSale}>
            <input type="number" min="1" placeholder="Product ID" value={saleForm.productId} onChange={(e) => setSaleForm({ ...saleForm, productId: e.target.value })} required />
            <label>
              Start Time
              <input type="datetime-local" value={saleForm.startTime} onChange={(e) => setSaleForm({ ...saleForm, startTime: e.target.value })} required />
            </label>
            <label>
              End Time
              <input type="datetime-local" value={saleForm.endTime} onChange={(e) => setSaleForm({ ...saleForm, endTime: e.target.value })} required />
            </label>
            <input type="number" min="0" placeholder="Sale Stock" value={saleForm.saleStock} onChange={(e) => setSaleForm({ ...saleForm, saleStock: e.target.value })} required />
            <input type="number" min="1" placeholder="Max Per User" value={saleForm.maxPerUser} onChange={(e) => setSaleForm({ ...saleForm, maxPerUser: e.target.value })} required />
            <input type="number" min="0.01" step="0.01" placeholder="Sale Price" value={saleForm.salePrice} onChange={(e) => setSaleForm({ ...saleForm, salePrice: e.target.value })} required />
            <button type="submit">Create Sale Event</button>
          </form>
        </section>
      </main>

      <section className="status-wrap">
        {loading && <p className="status">Loading sale events...</p>}
        {error && <p className="status error">{error}</p>}
        {success && <p className="status success">{success}</p>}
      </section>

      <section className="events">
        <div className="events-head">
          <h2>Sale Events</h2>
          <button onClick={loadEvents}>Refresh</button>
        </div>

        {events.length === 0 ? (
          <p className="empty">No sale events yet. Create a product and sale event first.</p>
        ) : (
          <div className="event-list">
            {events.map((event) => {
              const purchaseForm = purchaseForms[event.saleEventId] || initialPurchase;
              return (
                <article className="event-card" key={event.saleEventId}>
                  <div className="event-top">
                    <h3>{event.productName}</h3>
                    <span className={`badge ${event.status.toLowerCase()}`}>{event.status}</span>
                  </div>
                  <p className="sku">SKU: {event.productSku}</p>
                  <p>Event ID: {event.saleEventId}</p>
                  <p>Product ID: {event.productId}</p>
                  <p>Price: ${event.salePrice} (Regular: ${event.regularPrice})</p>
                  <p>Remaining Stock: {event.saleStock}</p>
                  <p>Limit/User: {event.maxPerUser}</p>
                  <p>Window: {new Date(event.startTime).toLocaleString()} - {new Date(event.endTime).toLocaleString()}</p>

                  <form className="purchase" onSubmit={(e) => handleJoinSale(e, event.saleEventId)}>
                    <input
                      type="number"
                      min="1"
                      placeholder="User ID"
                      value={purchaseForm.userId}
                      onChange={(e) => onPurchaseInputChange(event.saleEventId, 'userId', e.target.value)}
                      required
                    />
                    <input
                      type="number"
                      min="1"
                      placeholder="Quantity"
                      value={purchaseForm.quantity}
                      onChange={(e) => onPurchaseInputChange(event.saleEventId, 'quantity', e.target.value)}
                      required
                    />
                    <button type="submit" disabled={event.status !== 'LIVE'}>
                      Join Sale
                    </button>
                  </form>

                  {orders[event.saleEventId]?.orderId && (
                    <div className="purchase">
                      <p>Order: {orders[event.saleEventId].orderId}</p>
                      <p>Status: {orders[event.saleEventId].status}</p>
                      {orders[event.saleEventId].expiresAt && <p>Expires: {new Date(orders[event.saleEventId].expiresAt).toLocaleString()}</p>}
                      <button type="button" onClick={() => handleRefreshOrder(event.saleEventId)}>
                        Refresh Order
                      </button>
                      <button
                        type="button"
                        onClick={() => handlePayOrder(event.saleEventId)}
                        disabled={orders[event.saleEventId].status !== 'PENDING'}
                      >
                        Pay Now
                      </button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

export default App;
