import fs from 'node:fs';
const locales=['es','en','ca','eu','gl','de','fr','it','pt','pt-BR','jp','zh'];
for(const locale of locales){const value=JSON.parse(fs.readFileSync(new URL(`../src/locales/${locale}.json`,import.meta.url)));if(value?.ostris?.title!=='osTRIS')throw new Error(`invalid locale ${locale}`);}
const manifest=JSON.parse(fs.readFileSync(new URL('../src/generated/ostris/manifest.generated.json',import.meta.url)));if(manifest.module!=='ostris')throw new Error('invalid generated manifest');
const extension=fs.readFileSync(new URL('../src/extension.jsx',import.meta.url),'utf8');
if(/^import\s+React\s+from\s+["']react["']/m.test(extension))throw new Error('extension.jsx must use the shared React instance from window.__IDAX_MODULE_SDK__, not its own bundled copy (Invalid hook call / blank Shell)');
console.log('osTRIS frontend generated artifacts and 12 locales: OK');
