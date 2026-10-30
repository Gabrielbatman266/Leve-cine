# Leve Cine

Player leve de canais, filmes e séries para Android, celular e TV box.

- **Canais:** listas do [iptv-org](https://github.com/iptv-org/iptv) no GitHub (canais abertos e gratuitos: Brasil, Portugal, notícias, infantil…)
- **Filmes e séries:** acervo de domínio público do [Internet Archive](https://archive.org), na versão leve de 512 kb sempre que existe
- **Catálogo:** o arquivo `catalogo.json` deste repositório. Edite pelo próprio GitHub e o app atualiza ao abrir.

## Gerar o APK
1. Envie esta pasta para o repositório `Gabrielbatman266/Leve-cine` (branch `main`).
2. Abra **Actions → Gerar APK**. Ele roda sozinho a cada envio e também pode ser iniciado no botão *Run workflow*.
3. Quando terminar, baixe o artefato **LeveCine-apk**.

## Usar
- **Canais / Filmes / Séries / ★**: abas no topo
- **Lista ▾**: troca a lista (ex.: Brasil → Notícias; Clássicos → Terror)
- **Segurar** OK no controle (ou o dedo) num item: adiciona ou remove dos favoritos
- **⚙**: troca o link do catálogo, caso o repositório tenha outro nome

## Editar o catálogo
```json
"canais":  [{ "nome": "Brasil", "url": "https://...m3u" }],
"filmes":  [{ "nome": "Terror", "busca": "collection:feature_films AND subject:horror" }],
"series":  [{ "nome": "...", "busca": "consulta do archive.org" }],
"extras":  [{ "tipo": "filme|serie|canal", "titulo": "...", "url": "link direto", "archive": "id do archive.org" }]
```
