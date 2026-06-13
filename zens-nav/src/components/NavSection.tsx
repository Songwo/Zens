import type { NavCategoryMeta, NavItem as NavItemType } from "../types/nav";
import { NavItem } from "./NavItem";

type NavSectionProps = {
  category: NavCategoryMeta;
  items: NavItemType[];
};

export function NavSection({ category, items }: NavSectionProps) {
  return (
    <section
      id={category.title}
      className="border-t-2 border-ink pt-4"
    >
      <div className="border-b border-line pb-4">
        <h3 className="text-lg font-semibold text-ink">{category.title}</h3>
        <p className="mt-2 text-sm leading-6 text-muted">{category.description}</p>
      </div>
      <div className="divide-y-0">{items.map((item) => <NavItem key={item.id} item={item} />)}</div>
    </section>
  );
}
