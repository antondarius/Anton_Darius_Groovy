Este código de Groovy se encarga de actualizar una pagina de Wiki la cual contiene una tabla. Esta tabla se encarga de recoger mes por mes la cantidad de licencias de Jira en uso, disponibles y las licencias innecesarias. Para saber que licencias no son necesarias el codigo verifica si un usuario  ha iniciado sesion en los últimos 6 meses, y si no lo ha hecho la licencia de dicho usuario se considera inneecesaria, por lo que la empresa estaría pagándola para nada.

Este código se insertaría en un "Job" para que se ejecute mensualmente, así cada mes se actualizaría automáticamente
