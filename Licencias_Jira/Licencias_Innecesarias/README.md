Este código de Groovy se encarga de crear una página de Wiki la cual contiene una tabla. Este tabla almacena todos los usuarios de Jira cuya licencia es innecesaria. Este codigo verifica si un usuario con licencia ha iniciado sesión en los ultimos 6 meses, y si no lo ha hecho se almacenaría en una tabla. Esto nos sirve para posteriormente eliminar la licencia de los usuarios y recortar gastos innecesarios

Este código se insertaría en un "Job" para que se ejecute mensualmente, así cada mes se actualizaría automáticamente
