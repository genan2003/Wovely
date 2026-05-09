export interface Category {
    id: string;
    name: string;
    parentId: string | null;
    slug: string;
    level: number;
}
