# Requisitos do RestON

## Requisitos funcionais

**Equipe e acesso**
- RF01. O funcionário entra com login e senha.
- RF02. O sistema bloqueia o login depois de várias senhas erradas.
- RF03. A senha provisória precisa ser trocada no primeiro acesso.
- RF04. O administrador e o gerente cadastram os funcionários e escolhem o perfil (Administrador, Gerente, Garçom, Caixa, Cozinha ou Bartender).
- RF05. Cada perfil só abre as telas que pode usar.
- RF06. O sistema registra as ações importantes (auditoria), e só o administrador vê esse registro.

**Salão**
- RF07. Cadastrar mesas por setor e ver quais estão livres, ocupadas, reservadas ou esperando limpeza.
- RF08. Abrir e encerrar o atendimento de uma mesa e trocar o cliente de mesa.
- RF09. Fazer reservas por dia e horário, sem conflito de horário nem de lugares.

**Cardápio e estoque**
- RF10. Cadastrar categorias e produtos com preço, descrição, foto e tempo de preparo.
- RF11. Definir para onde o produto vai: cozinha, bar ou sem preparo.
- RF12. Marcar produto como indisponível ou arquivá-lo.
- RF13. Controlar o estoque das bebidas prontas: entrada, contagem e estoque mínimo. Cada pedido baixa o saldo.

**Comandas e pedidos**
- RF14. Abrir uma comanda por cliente na mesma mesa.
- RF15. Lançar pedido pelo celular do garçom, com observações por item.
- RF16. O pedido se divide sozinho entre cozinha e bar.
- RF17. Cancelar item com motivo e transferir itens entre comandas.

**Cozinha e bar**
- RF18. A cozinha e o bar veem os pedidos e passam cada um de recebido para em preparo e pronto.
- RF19. Pedido atrasado fica destacado em vermelho.

**Caixa**
- RF20. Abrir e fechar o turno do caixa, conferindo o dinheiro contado com o esperado.
- RF21. Receber a conta dividida em PIX, cartão e dinheiro (pagamentos simulados), com troco.
- RF22. Fechar a comanda só quando tudo estiver pago, e emitir o comprovante.

**Gestão**
- RF23. Mostrar a visão geral do dia: faturamento, ticket médio, mesas ocupadas e pedidos em andamento.
- RF24. Gerar relatórios de faturamento por dia, forma de pagamento e produtos mais vendidos.

## Requisitos não funcionais

- RNF01. Os pedidos aparecem na cozinha e no salão sem precisar recarregar a página.
- RNF02. As telas funcionam no computador e no celular.
- RNF03. A senha é guardada criptografada e a sessão usa cookie seguro.
- RNF04. As regras de permissão valem na API, e não só no menu.
- RNF05. Os dados pessoais são poucos (funcionários e telefone de reservas), e as reservas antigas são anonimizadas depois de 90 dias.
- RNF06. O banco de dados é o PostgreSQL, criado e atualizado pelo Flyway.
- RNF07. O sistema tem testes automáticos no backend, no frontend e no navegador.
- RNF08. O modo demonstração deixa o sistema preenchido para apresentar, e fica desligado por padrão.
