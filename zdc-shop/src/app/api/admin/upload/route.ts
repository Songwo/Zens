import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/admin-guard";
import {
  ALLOWED_IMAGE_TYPES,
  MAX_IMAGE_BYTES,
  generateObjectKey,
  presignPut,
  publicUrlFor,
} from "@/lib/r2";

/**
 * POST /api/admin/upload
 *
 * 申请一个 Cloudflare R2 presigned PUT URL。
 * 浏览器拿到 uploadUrl 之后,自己 PUT 文件到 R2,不再经过子站服务器。
 *
 * 请求 JSON: { filename, contentType, size }
 * 响应 JSON: { uploadUrl, publicUrl, key, headers, expiresIn }
 */

const Body = z.object({
  filename: z.string().min(1).max(255),
  contentType: z.string().min(1),
  size: z.number().int().positive().max(MAX_IMAGE_BYTES, "图片不能超过 5MB"),
  prefix: z.string().max(64).optional(),
});

const EXPIRES_IN = 60; // seconds

export async function POST(req: NextRequest) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;

  const parsed = Body.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; "),
      },
      { status: 400 }
    );
  }
  const { contentType, size, prefix } = parsed.data;

  if (!ALLOWED_IMAGE_TYPES.has(contentType)) {
    return NextResponse.json(
      { error: "BAD_TYPE", message: `不支持的图片类型: ${contentType}` },
      { status: 400 }
    );
  }

  const key = generateObjectKey({ prefix: prefix ?? "products", contentType });

  let uploadUrl: string;
  try {
    uploadUrl = await presignPut({ key, contentType, contentLength: size, expiresIn: EXPIRES_IN });
  } catch (e) {
    console.error("[upload] presign 失败", e);
    return NextResponse.json(
      { error: "PRESIGN_FAILED", message: "签发上传链接失败,请检查 R2 配置" },
      { status: 500 }
    );
  }

  return NextResponse.json({
    ok: true,
    uploadUrl,
    publicUrl: publicUrlFor(key),
    key,
    headers: {
      "Content-Type": contentType,
    },
    expiresIn: EXPIRES_IN,
  });
}
