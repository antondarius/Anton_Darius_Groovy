Este código de Groovy tiene dos funcionalidades:
La primera se encarga de crear una página de Wiki la cual contiene una tabla. Esta tabla almacena todos los usuarios de Wiki por los cuales la empresa esta pagando una licencia pero es innecesaria. Lo que hace el código es comprobar de todos los usuarios licenciados los que no han iniciado sesión en los últimos 6 meses. Estos se almacenarían en una tabla, la cual posteriormente permitiría a la empresa borrar estos usuarios recortando gastos innecesarios. 
La segunda funcionalidad se encarga de actualizar una página la cual contiene una tabla. Esta tabla almacena mes a mes la cantidad de licencias de Wiki en uso y disponibles, así como las innecesarias. Esto nos sirve para tener un control de la cantidad de licencias, podiendo realizar un gráfico mensual del crecimiento de usuarios de la empresa

Este código se insertaría en un "Job" para que se ejecute mensualmente, así cada mes se actualizaría automáticamente. 
