# Frontend

Interface do RestON em Angular 21 (componentes standalone).

## Como rodar

```bash
npm install
npm start
```

O app abre em http://localhost:4200 e conversa com a API em http://localhost:8080 (o `proxy.conf.json` faz o redirecionamento).

## Testes e build

```bash
npm test        # testes unitários (Vitest)
npm run e2e     # testes ponta a ponta (Playwright), precisa da API e do banco no ar
npx ng build    # gera a pasta dist/
```

O passo a passo completo, com o banco e a API, está no [README da raiz](../README.md).
