export default function AnalysisSection({ title, children }) {
  return <details className="panel p-5 mt-6">
    <summary className="cursor-pointer font-medium">View analysis · {title}</summary>
    <div className="mt-5 min-w-0">{children}</div>
  </details>;
}
