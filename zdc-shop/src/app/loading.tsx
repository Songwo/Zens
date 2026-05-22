export default function Loading() {
  return (
    <main className="editorial-container animate-fade-in pt-24 pb-32">
      <div className="skeleton h-3 w-32" />
      <div className="skeleton mt-5 h-12 w-3/4" />
      <div className="skeleton mt-3 h-12 w-1/2" />
      <div className="skeleton mt-8 h-12 w-44 rounded-pill" />

      <div className="mt-20 h-px w-full bg-divider" />
      <div className="mt-12 skeleton aspect-[16/7] w-full rounded-3xl" />

      <div className="mt-20 space-y-6">
        <div className="skeleton h-5 w-40" />
        <div className="space-y-4">
          <div className="skeleton h-16 w-full rounded-xl" />
          <div className="skeleton h-16 w-full rounded-xl" />
          <div className="skeleton h-16 w-full rounded-xl" />
        </div>
      </div>
    </main>
  );
}
