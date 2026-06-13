export type NavStatus = "normal" | "maintenance" | "coming-soon" | "beta";

export type NavCategory =
  | "community-core"
  | "subsystems"
  | "community-tools"
  | "learning"
  | "rules"
  | "social-feedback";

export type NavItem = {
  id: string;
  title: string;
  description: string;
  href: string;
  category: NavCategory;
  icon: string;
  status?: NavStatus;
  tag?: string;
  external?: boolean;
  quick?: boolean;
  system?: string;
  localHref?: string;
  productionHref?: string;
  integration?: string;
  keywords?: string[];
};

export type NavCategoryMeta = {
  id: NavCategory;
  title: string;
  description: string;
};

export type IntegrationFlow = {
  id: string;
  title: string;
  from: string;
  to: string;
  icon: string;
  description: string;
  touchpoints: string[];
};
