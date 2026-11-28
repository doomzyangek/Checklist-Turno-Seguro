# Evidências · C1 Cadastro de modelo e C2 Preenchimento do checklist

Responsável pelo teste: Danilo · Revisor: _(preencher no PR)_  
Versão testada: APK de debug 0.1.0 (`main` em `73c49aa`) · Aparelho: _(modelo e versão do Android)_ · Data: _(dd/mm/aaaa)_

Este roteiro cobre as histórias **C1** e **C2** de [Historias_CORE.md](../../Historias_CORE.md) e, no caminho, os itens **NC1**, **NC6** e **NC7** do núcleo comum. Os prints ficam nesta pasta com o nome indicado em cada passo.

## Preparação

1. Regras de `firestore.rules` publicadas no console do Firebase.
2. Duas contas de teste com `usuarios/{uid}.perfil` = `supervisor` e `operador`.
3. No Firestore, a máquina `maquinas/torno-01` com `nome` = `Torno 01` e `tipo` = `torno`. O tipo `torno` é usado só neste teste, para não interferir nos testes do modelo `prensa`.

## NC1 · NC6 · NC7 — Cadastro de conta e login

| # | Passo | Resultado esperado | Print | OK? |
|---|---|---|---|---|
| A1 | Em **Criar conta**, enviar com o e-mail `teste@` | Mensagem "Informe um e-mail válido." e o app não fecha | `a1-email-invalido.png` | |
| A2 | Enviar com senha de 3 caracteres | Mensagem "Use pelo menos 6 caracteres." | `a2-senha-curta.png` | |
| A3 | Digitar e-mail e senha e girar a tela | Os dois campos continuam preenchidos | `a3-rotacao-login.png` | |
| A4 | Criar uma conta nova válida, sem documento de perfil | A conta entra e mostra "Seu acesso está pendente…" | `a4-perfil-pendente.png` | |
| A5 | Fechar o app pelo multitarefa e abrir de novo | Continua logado | — | |
| A6 | Tocar em **Sair** | Volta para o formulário de login | — | |
| A7 | Entrar com a conta de supervisor e com a de operador | O rótulo mostra **Supervisor** e **Operador** | `a7-supervisor.png`, `a7-operador.png` | |

## C1 — Cadastro de modelo (conta Supervisor)

| # | Passo | Resultado esperado | Print | OK? |
|---|---|---|---|---|
| B1 | Tocar em **Cadastrar modelo**, digitar o tipo `Torno` (com maiúscula) e salvar | Mensagem de erro no campo do tipo; nada é gravado | `b1-tipo-invalido.png` | |
| B2 | Corrigir para `torno`, deixar a descrição do item 1 vazia e salvar | "Descreva o item." no campo vazio; o formulário continua aberto | `b2-item-vazio.png` | |
| B3 | Preencher 3 itens: "Proteção da placa no lugar" (**crítico**), "Botão de emergência funciona" (**crítico**), "Área limpa e sem óleo no piso" (não crítico) | Os três itens aparecem, dois com **Item crítico** marcado | `b3-modelo-preenchido.png` | |
| B4 | Girar a tela antes de salvar | Tipo, textos e marcações continuam iguais | `b4-rotacao-modelo.png` | |
| B5 | Tocar em **Remover item** até sobrar um | O último item não sai: "O modelo precisa ter pelo menos um item." (refazer B3 depois) | — | |
| B6 | Tocar em **Salvar modelo** | "Modelo salvo: torno" | `b6-modelo-salvo.png` | |
| B7 | No console, abrir `modelos/torno` | Campo `itens` com 3 mapas e `critico` = `true`, `true`, `false` | `b7-firestore-modelo.png` | |
| B8 | Tentar cadastrar `torno` de novo | "Já existe um modelo para esse tipo." | `b8-modelo-duplicado.png` | |

## C2 — Preenchimento do checklist (conta Operador)

| # | Passo | Resultado esperado | Print | OK? |
|---|---|---|---|---|
| C1 | Abrir a lista de máquinas | **Torno 01** aparece na lista | `c1-lista-maquinas.png` | |
| C2 | Tocar em **Torno 01** | Os 3 itens do modelo `torno`, na ordem cadastrada, os críticos com "· Crítico", cada um com **Conforme** e **Não conforme** | `c2-formulario.png` | |
| C3 | Responder só os itens 1 e 2 e tocar em **Enviar inspeção** | "Responda ao item 3." e nada é gravado | `c3-item-pendente.png` | |
| C4 | Marcar o item 3 como **Não conforme** | Aparece o campo "Observação obrigatória" | — | |
| C5 | Escrever a observação, girar a tela | Respostas e observação continuam iguais | `c5-rotacao-formulario.png` | |
| C6 | Mudar o item 3 para **Conforme**, deixar todos conformes e enviar | "Inspeção registrada. Máquina liberada." | `c6-enviado.png` | |
| C7 | No console, abrir o documento novo em `inspecoes` | `maquinaId` = `torno-01`, `tipo` = `torno`, 3 respostas e `resultado` = `liberada` | `c7-firestore-inspecao.png` | |

Os testes de observação obrigatória (C3), bloqueio e liberação (C4) e painel e histórico (C5 e C6) estão com Enzo, Fabiano e Guilherme. Depois do passo C6, a máquina `torno-01` fica liberada e pode ser usada por eles.

## Resultado

- [ ] Todos os passos acima conferidos no aparelho
- [ ] Prints salvos nesta pasta
- [ ] Linhas **C1**, **C2**, **NC1**, **NC6** e **NC7** atualizadas em `DOCS/Checklist_CORE_e_Nucleo_Comum.docx`

Problemas encontrados: _(descrever aqui, ou "nenhum")_
