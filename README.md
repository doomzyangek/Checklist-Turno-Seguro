# Turno Seguro

Projeto 2 · Checklist Pré-Turno

**Equipe:** Danilo, Enzo, Fabiano e Guilherme  
**Turma:** ADS, Turma A, 4º semestre, noturno · SENAI Armando de Arruda Pereira

O Turno Seguro é um aplicativo acadêmico de inspeção antes do início da operação. A proposta é registrar o checklist por máquina e informar ao supervisor quando uma não conformidade crítica exige o bloqueio no sistema.

## Desenvolvimento local

1. Configure um JDK 17 e o Android SDK com a plataforma Android 36. É possível usar o Android Studio ou as ferramentas de linha de comando do Android SDK.
2. No Firebase, registre um aplicativo Android com o identificador `br.com.turnoseguro.app` e coloque o arquivo de configuração em `app/google-services.json`. Esse arquivo fica fora do Git.
3. Abra a raiz deste projeto no Android Studio para sincronizar o Gradle ou execute `./gradlew assembleDebug` no Linux/macOS (`gradlew.bat assembleDebug` no Windows).

Para compilar sem Android Studio, use as ferramentas de linha de comando do SDK. Com `JAVA_HOME` apontando para o JDK 17 e `ANDROID_HOME` para o SDK, execute `sdkmanager --licenses`, depois `sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"` e `./gradlew assembleDebug`. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

Para instalar o APK, copie o arquivo para um celular Android 8.0 ou superior e abra-o pelo gerenciador de arquivos. Se o Android solicitar, autorize a instalação de apps por essa origem.

## Configurar o Firebase

1. No console do projeto associado ao arquivo de configuração, ative **Authentication → Método de login → E-mail/senha**.
2. Crie um banco **Cloud Firestore**. Para o projeto dedicado ao Turno Seguro, publique a versão atual de `firestore.rules` pela aba **Regras** do console. Se o grupo já usa Firebase CLI, a alternativa é `firebase deploy --only firestore:rules --project ID_DO_PROJETO`. Cada conta pode consultar apenas o próprio documento de perfil. Operadores e supervisores podem consultar máquinas e modelos. Só supervisores podem criar modelos pelo app. A equipe gerencia perfis e máquinas pelo console.
3. Cadastre duas contas de teste, pelo aplicativo ou pelo console do Authentication. Copie o UID de cada uma e crie no Firestore um documento `usuarios/{uid}`. Adicione o campo de texto `perfil`, com valor `operador` numa conta e `supervisor` na outra. Use senhas exclusivas de demonstração e compartilhe as credenciais de teste com o grupo por um canal privado.

## Roteiro de teste do acesso

1. Rode o app num emulador ou em um aparelho com Android 8.0 (API 26) ou superior e conexão à internet. Para o aparelho físico, use a opção **Run** do Android Studio com depuração USB ou instale o APK de debug.
2. Em **Criar conta**, tente enviar com e-mail inválido e senha curta. Confira as mensagens em português. Digite os dados e gire a tela para conferir se a edição foi preservada.
3. Crie uma conta sem documento de perfil e confirme que ela fica com acesso pendente. Crie o documento pelo console e confirme que o perfil aparece na tela sem novo login.
4. Entre com as contas de operador e supervisor, confira os rótulos e feche e reabra o aplicativo para conferir a sessão. Use **Sair** e confirme o retorno ao formulário.

## Estado atual

O app inclui cadastro, login, perfis, modelos, envio da inspeção, desbloqueio com justificativa, painel diário do supervisor e histórico por máquina. O APK de debug da versão 0.1.0 foi compilado e sua assinatura foi verificada. A validação dos fluxos no celular, com o Firebase e as contas de demonstração, ainda está pendente.

O [checklist de requisitos](DOCS/Checklist_CORE_e_Nucleo_Comum.docx) registra o que falta validar. As [histórias CORE](DOCS/Historias_CORE.md) trazem os critérios de aceite para o teste e para as issues do GitHub Projects.

## Preparar e testar um checklist

1. No console do Firestore, em **Dados**, crie a coleção `maquinas` com um documento de ID `prensa-01`. Adicione os campos de texto `nome` com valor `Prensa 01` e `tipo` com valor `prensa`.
2. No app, entre como Supervisor e cadastre o modelo `prensa` com pelo menos um item. Marque, por exemplo, “Botão de emergência funciona” como crítico. O modelo aceita até 10 itens e é criado uma vez por tipo.
3. Entre como Operador, selecione **Prensa 01** e envie um checklist com todos os itens conformes. Confira se a confirmação indica que a máquina foi liberada.
4. Faça outra inspeção. Marque um item crítico como **Não conforme** e tente enviar sem observação. Depois escreva a observação, gire a tela para conferir o rascunho, responda aos itens restantes e toque em **Enviar inspeção**. A máquina deve aparecer como bloqueada. O operador precisa aguardar a liberação para fazer outra inspeção.
5. Entre como Supervisor, selecione **Prensa 01** no painel diário e toque em **Desbloquear máquina**. Confira que a tela exige uma justificativa. Depois confirme o desbloqueio e confira o registro na coleção `liberacoes`. A inspeção original permanece em `inspecoes`.

A inspeção e o status são gravados na mesma transação do Firestore. O desbloqueio também grava a mudança de status e seu registro de auditoria juntos. Os fluxos de envio e desbloqueio precisam de conexão. Para testá-los, publique a versão atual de `firestore.rules` no console antes de rodar o app.

## Configurar e testar o painel e o histórico

1. No **Firestore → Índices**, crie um índice composto para a coleção `inspecoes`, com escopo **Coleção**. Adicione `maquinaId` em ordem ascendente e `data` em ordem descendente. Aguarde o índice ficar pronto. Com Firebase CLI, é possível publicar regras e índice juntos com `firebase deploy --only firestore:rules,firestore:indexes --project ID_DO_PROJETO`.
2. Deixe o painel do Supervisor aberto enquanto usa a conta de Operador em outro aparelho. Confira a mudança automática de **Sem inspeção** para **Liberada** após um checklist conforme e para **Bloqueada** após uma não conformidade crítica.
3. Selecione a máquina no painel e confira o e-mail do operador, a data e o resultado de cada inspeção, da mais recente para a mais antiga. O histórico carrega 30 registros por vez e permite mostrar mais quando houver.

O painel usa o dia de São Paulo. Um bloqueio permanece visível mesmo quando vem de uma inspeção de outro dia. Se essa máquina for desbloqueada sem ter sido inspecionada hoje, ela aparece como **Sem inspeção**.
