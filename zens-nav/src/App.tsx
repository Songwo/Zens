import { useMemo, useState } from "react";
import { Footer } from "./components/Footer";
import { Header } from "./components/Header";
import { Hero } from "./components/Hero";
import { IntegrationMap } from "./components/IntegrationMap";
import { NavSection } from "./components/NavSection";
import { QuickLinks } from "./components/QuickLinks";
import { SearchBar } from "./components/SearchBar";
import { categories, integrationFlows, navItems } from "./data/navItems";
import type { NavItem } from "./types/nav";

function normalize(value: string) {
  return value.trim().toLowerCase();
}

function matchesQuery(item: NavItem, query: string) {
  if (!query) {
    return true;
  }

  return normalize(
    [
      item.title,
      item.description,
      item.category,
      item.icon,
      item.status,
      item.tag,
      item.system,
      item.integration,
      item.href,
      item.localHref,
      item.productionHref,
      ...(item.keywords ?? []),
    ]
      .filter(Boolean)
      .join(" ")
  ).includes(query);
}

export function App() {
  const [query, setQuery] = useState("");
  const normalizedQuery = normalize(query);

  const filteredItems = useMemo(
    () => navItems.filter((item) => matchesQuery(item, normalizedQuery)),
    [normalizedQuery]
  );

  const quickItems = useMemo(() => navItems.filter((item) => item.quick), []);

  return (
    <div className="min-h-screen bg-canvas text-ink">
      <Header />
      <main className="animate-fade-in">
        <Hero />
        <SearchBar value={query} resultCount={filteredItems.length} totalCount={navItems.length} onChange={setQuery} />
        <QuickLinks items={quickItems} />
        <IntegrationMap flows={integrationFlows} />

        <section className="mx-auto max-w-7xl px-5 pb-12 sm:px-7 lg:px-8" aria-labelledby="directory-title">
          <div className="mb-5 flex flex-col gap-2 border-b border-line pb-5 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-normal text-amber-strong">Directory</p>
              <h2 id="directory-title" className="text-2xl font-semibold text-ink">
                真实入口清单
              </h2>
            </div>
            <p className="text-sm text-muted">基于当前项目路由、子站源码和环境配置整理，可用于本地联调和线上跳转。</p>
          </div>

          {filteredItems.length ? (
            <div className="grid gap-4 transition-opacity md:grid-cols-2 xl:grid-cols-3">
              {categories.map((category) => {
                const items = filteredItems.filter((item) => item.category === category.id);

                if (!items.length) {
                  return null;
                }

                return <NavSection key={category.id} category={category} items={items} />;
              })}
            </div>
          ) : (
            <div className="rounded-nav border border-line bg-surface px-5 py-10 text-center">
              <p className="text-base font-semibold text-ink">没有找到相关入口，试试搜索其他关键词。</p>
              <p className="mt-2 text-sm text-muted">可以搜索服务名称、文档类型、工具用途或状态标签。</p>
            </div>
          )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
