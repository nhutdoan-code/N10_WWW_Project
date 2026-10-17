export default function FormField({ label, error, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} className={error ? 'invalid' : ''} />
      {error && <small className="error">{error}</small>}
    </label>
  );
}
