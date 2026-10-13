/*
 * Script clássico (não é módulo), para rodar mesmo onde os módulos falham.
 * Se o app não iniciar em poucos segundos, mostra o motivo provável e como resolver,
 * em vez de deixar a página parada em "Conectando à API".
 */
(function () {
  var APP_URL = 'http://localhost:8080/';
  var openedAsFile = location.protocol === 'file:';

  function showError(message) {
    document.getElementById('boot-error-message').textContent = message;
    document.getElementById('boot-error').hidden = false;
  }

  if (openedAsFile) {
    // Aberto como arquivo, o navegador bloqueia os módulos. Se a aplicação estiver no ar, vai para ela.
    fetch(APP_URL, { mode: 'no-cors' })
      .then(function () { location.replace(APP_URL + location.hash); })
      .catch(function () {
        showError('O arquivo foi aberto direto do disco e a aplicação não está rodando. A página precisa da aplicação para funcionar.');
      });
    return;
  }

  setTimeout(function () {
    if (document.documentElement.dataset.app === 'ready') return;
    showError('O navegador não executou o JavaScript da página. Pode ser uma versão antiga guardada em cache ou um navegador desatualizado.');
  }, 4000);
})();
