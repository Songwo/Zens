export function Footer() {
  const links = [
    { label: "关于我们", href: "https://www.allinsong.top/about" },
    { label: "用户协议", href: "https://www.allinsong.top/terms" },
    { label: "隐私政策", href: "https://www.allinsong.top/privacy" },
    { label: "联系管理", href: "https://www.allinsong.top/contact" },
  ];

  return (
    <footer className="mt-12 border-t border-line bg-surface">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-7 text-sm text-muted sm:px-7 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <div>
          <p className="font-semibold text-ink">Zens</p>
          <p className="mt-1">探索知识，分享生活，结识同好</p>
        </div>
        <nav className="flex flex-wrap gap-x-5 gap-y-2" aria-label="页脚链接">
          {links.map((link) => (
            <a key={link.href} href={link.href} className="hover:text-ink">
              {link.label}
            </a>
          ))}
        </nav>
        <p>© 2026 Zens</p>
      </div>
    </footer>
  );
}
