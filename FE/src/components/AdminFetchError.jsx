export default function AdminFetchError({ error, onRetry, loading = false }) {
  if (!error) return null;

  return (
    <div className="admin-fetch-error" role="alert">
      <span>{error}</span>
      {onRetry && (
        <button type="button" className="btn" onClick={onRetry} disabled={loading}>
          {loading ? 'Đang tải…' : 'Tải lại'}
        </button>
      )}
    </div>
  );
}
