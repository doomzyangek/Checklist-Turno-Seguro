# Histórias de usuário do Turno Seguro

Projeto 2 · Checklist Pré-Turno

Use os critérios abaixo para validar a demonstração no aparelho. Mova a issue para **Feito** no GitHub Projects após conferir seus critérios e registrar a evidência no checklist da equipe.

## C1 Cadastro de modelo

Como supervisor, quero cadastrar os itens do checklist por tipo de máquina para definir o que o operador deve verificar.

- **Dado** um tipo válido sem modelo, **quando** eu salvar itens com e sem a marcação de crítico, **então** o modelo fica disponível para as máquinas desse tipo.
- **Dado** um modelo com item sem descrição, **quando** eu tentar salvá-lo, **então** o app indica o campo obrigatório e mantém o formulário aberto.

## C2 Preenchimento do checklist

Como operador, quero abrir o checklist da máquina escolhida para responder aos itens do modelo correto.

- **Dado** que a máquina está disponível e tem um modelo, **quando** eu a selecionar, **então** o app mostra os itens do tipo cadastrado e oferece as opções **Conforme** e **Não conforme** em cada item.
- **Dado** um checklist com um item sem resposta, **quando** eu tentar enviar, **então** o app indica o item pendente e impede a gravação.

## C3 Observação obrigatória

Como operador, quero informar o que observei em cada item marcado como não conforme para que o supervisor saiba o que aconteceu.

- **Dado** um item marcado como **Não conforme** sem observação, **quando** eu tentar enviar, **então** o app aponta o campo obrigatório e a inspeção não é gravada.
- **Dado** que todos os itens estão respondidos e cada não conformidade tem observação, **quando** eu enviar, **então** a inspeção é gravada com as observações informadas.

## C4 Bloqueio e liberação

Como supervisor, quero que uma não conformidade crítica bloqueie a máquina e que toda liberação seja justificada para manter o registro da decisão.

- **Dado** que o operador respondeu **Não conforme** a um item crítico e informou uma observação, **quando** a inspeção for salva, **então** a máquina passa para **Bloqueada**.
- **Dado** que uma máquina está bloqueada, **quando** um operador consultar a máquina, **então** ele não tem acesso à ação de desbloqueio.
- **Dado** que uma máquina está bloqueada, **quando** eu tentar liberá-la sem justificativa, **então** a operação é recusada. **Quando** eu informar a justificativa e confirmar, **então** a liberação fica registrada com meu usuário, a justificativa e a data.

## C5 Painel diário

Como supervisor, quero consultar o estado das máquinas no painel para identificar quais foram inspecionadas e quais estão bloqueadas.

- **Dado** que uma máquina ainda não teve inspeção no dia e não tem bloqueio ativo, **quando** eu abrir o painel, **então** ela aparece como **Sem inspeção**.
- **Dado** o painel aberto em um aparelho, **quando** outro aparelho registrar uma inspeção conforme ou uma não conformidade crítica, **então** a máquina aparece como **Liberada** ou **Bloqueada**, respectivamente, sem recarregar a tela.

## C6 Histórico por máquina

Como supervisor, quero ver as inspeções de uma máquina para saber quem respondeu ao checklist e quando.

- **Dado** que a máquina tem duas inspeções registradas, **quando** eu abrir seu histórico, **então** o app mostra data, operador e resultado de cada inspeção, da mais recente para a mais antiga.
- **Dado** que a máquina não tem inspeções, **quando** eu abrir seu histórico, **então** o app informa que ela ainda não tem registros.
