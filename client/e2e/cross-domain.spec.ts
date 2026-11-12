import { expect, test, type Page } from '@playwright/test';

const A = 'http://localhost:5173';
const B = 'http://localhost:5174';
const PASSWORD = 'Str0ngPassw0rd';
const run = `${Date.now() % 1_000_000}`;
const SHOTS = 'e2e/screenshots';

async function register(page: Page, base: string, username: string, displayName: string) {
  await page.goto(`${base}/register`);
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('显示名称').fill(displayName);
  await page.getByLabel('密码', { exact: true }).fill(PASSWORD);
  await page.getByLabel('确认密码').fill(PASSWORD);
  await page.getByRole('button', { name: '创建账号' }).click();
  await expect(page.getByText('账号已创建，请登录。')).toBeVisible();
}

async function login(page: Page, password = PASSWORD) {
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
}

async function compose(page: Page, to: string, subject: string, body: string) {
  await page.getByRole('button', { name: '写邮件' }).first().click();
  const drawer = page.getByRole('dialog');
  await drawer.getByLabel('收件人').fill(to);
  await drawer.getByLabel('主题').fill(subject);
  await drawer.getByLabel('正文').fill(body);
  await drawer.getByRole('button', { name: '发送', exact: true }).click();
}

test('mail travels from domain-a to domain-b and back through the UI', async ({ browser }) => {
  const alice = `alice${run}`;
  const bob = `bob${run}`;
  const subject = `季度计划 ${run}`;
  const xss = '<img src=x onerror="window.__pwned=1"><script>window.__pwned=1</script>';

  // Two separate browser contexts = two people on two domains.
  const aliceCtx = await browser.newContext();
  const bobCtx = await browser.newContext();
  const a = await aliceCtx.newPage();
  const b = await bobCtx.newPage();

  await a.goto(`${A}/login`);
  await expect(a.getByRole('heading', { level: 1 })).toContainText('domain-a.test');
  await a.screenshot({ path: `${SHOTS}/01-login-domain-a.png` });

  await register(a, A, alice, 'Alice 王');
  await login(a);
  await expect(a.getByRole('heading', { name: /收件箱/ })).toBeVisible();

  await register(b, B, bob, 'Bob 李');
  await login(b);
  await expect(b.getByRole('heading', { name: /收件箱/ })).toBeVisible();
  await b.screenshot({ path: `${SHOTS}/02-empty-inbox-domain-b.png` });

  // A -> B, with an XSS probe in the body.
  await compose(a, `${bob}@domain-b.test`, subject, `Bob，你好：\n\n这是跨域测试。\n${xss}`);
  await expect(a).toHaveURL(/\/mail\/sent\/\d+/);
  const receipt = a.getByRole('region', { name: '投递回执' });
  await expect(receipt.getByText('已送达')).toBeVisible({ timeout: 15_000 });
  await a.screenshot({ path: `${SHOTS}/03-sent-with-receipt-domain-a.png` });

  // Bob sees it on domain-b; the probe is shown as text and does not execute.
  await b.getByRole('button', { name: '刷新' }).click();
  await b.getByRole('link', { name: new RegExp(subject) }).click();
  await expect(b.getByRole('heading', { name: subject })).toBeVisible();
  // The probe shows up as literal text in the body (and in the list preview), never as markup.
  await expect(b.locator('.reader__body')).toContainText(xss);
  expect(await b.locator('.reader__body img, .reader__body script').count()).toBe(0);
  expect(await b.evaluate(() => (window as unknown as { __pwned?: number }).__pwned)).toBeUndefined();
  await b.screenshot({ path: `${SHOTS}/04-read-on-domain-b.png` });

  // Bob replies; the reply arrives back on domain-a.
  await b.getByRole('button', { name: '回复', exact: true }).click();
  const drawer = b.getByRole('dialog');
  await expect(drawer.getByLabel('主题')).toHaveValue(`Re: ${subject}`);
  await b.screenshot({ path: `${SHOTS}/05-reply-compose-domain-b.png` });
  await drawer.getByRole('button', { name: '发送', exact: true }).click();
  await expect(b.getByRole('region', { name: '投递回执' }).getByText('已送达')).toBeVisible({ timeout: 15_000 });

  await a.getByRole('link', { name: '收件箱' }).click();
  await a.getByRole('button', { name: '刷新' }).click();
  await a.getByRole('link', { name: new RegExp(`Re: ${subject}`) }).click();
  await expect(a.getByRole('heading', { name: `Re: ${subject}` })).toBeVisible();
  await a.screenshot({ path: `${SHOTS}/06-reply-received-domain-a.png` });

  await aliceCtx.close();
  await bobCtx.close();
});

test('unknown recipients are explained, drafts survive, and sign-out ends the session', async ({ page }) => {
  const carol = `carol${run}`;
  await register(page, A, carol, 'Carol 张');
  await login(page);

  // Unknown local user: the send is refused with a per-address reason.
  await compose(page, `nobody${run}@domain-a.test`, '无人收', 'x');
  await expect(page.getByText('本域没有这个用户')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/07-recipient-error.png` });

  // Save it as a draft instead, then find it in the drafts folder.
  const drawer = page.getByRole('dialog');
  await drawer.getByLabel('主题').fill(`草稿 ${run}`);
  await drawer.getByRole('button', { name: '保存草稿' }).click();
  await expect(page.getByText('草稿已保存')).toBeVisible();
  await drawer.getByRole('button', { name: /关闭|Close/ }).click();
  await page.getByRole('link', { name: '草稿箱' }).click();
  await page.getByRole('link', { name: new RegExp(`草稿 ${run}`) }).click();
  await expect(page.getByRole('button', { name: '继续编辑' })).toBeVisible();

  // Sign out: going back to the mailbox requires signing in again.
  await page.getByRole('button', { name: '退出登录' }).first().click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto(`${A}/mail/inbox`);
  await expect(page).toHaveURL(/\/login/);
});

test('works at phone width: one pane at a time', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  const dan = `dan${run}`;
  await page.goto(`${A}/login`);
  await page.screenshot({ path: `${SHOTS}/08-mobile-login.png` });
  await register(page, A, dan, 'Dan 赵');
  await login(page);
  await expect(page.getByRole('heading', { name: /收件箱/ })).toBeVisible();
  await expect(page.getByRole('link', { name: '已发送' })).toBeVisible();
  // The reading pane is hidden until a message is opened.
  await expect(page.getByText('选择一封邮件')).toBeHidden();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow, 'no horizontal page scroll').toBe(false);
  await page.screenshot({ path: `${SHOTS}/09-mobile-inbox.png` });
  await ctx.close();
});
