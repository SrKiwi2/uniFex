/**
 *  Form Wizard
 */
'use strict';

(function () {
  const select2 = $('.select2');
  const selectPicker = $('.selectpicker');

  // Contenedor del stepper
  const wizardValidation = document.querySelector('#wizard-validation');
  if (!wizardValidation) return;

  // Form y pasos
  const wizardValidationForm = wizardValidation.querySelector('#wizard-validation-form');
  const step1 = wizardValidationForm.querySelector('#account-details-validation');
  const step2 = wizardValidationForm.querySelector('#personal-info-validation');
  const step3 = wizardValidationForm.querySelector('#social-links-validation');
  const step4 = wizardValidationForm.querySelector('#pago-links-validation');

  const btnNext = [].slice.call(wizardValidationForm.querySelectorAll('.btn-next'));
  const btnPrev = [].slice.call(wizardValidationForm.querySelectorAll('.btn-prev'));

  const validationStepper = new Stepper(wizardValidation, { linear: true });

  // --- Paso 1
  const FormValidation1 = FormValidation.formValidation(step1, {
    fields: {
      formValidationNombreEntidad: {
        selector: '#formValidationNombreEntidad',
        validators: {
          notEmpty: { message: 'El campo es requerido' },
          stringLength: { min: 6, max: 30, message: 'Entre 6 y 30 caracteres' },
          regexp: { regexp: /^[a-zA-Z0-9 ]+$/, message: 'Solo alfanumérico y espacios' }
        }
      },
      formValidationNit: {
        selector: '#formValidationNit',
        validators: {
          notEmpty: { message: 'El campo es requerido' },
          stringLength: { min: 6, max: 30, message: 'Entre 6 y 30 caracteres' },
          regexp: { regexp: /^[a-zA-Z0-9 ]+$/, message: 'Solo alfanumérico y espacios' }
        }
      },
      formValidationDescripcion: {
        selector: '#formValidationDescripcion',
        validators: {
          notEmpty: { message: 'El campo es requerido' },
          stringLength: { min: 6, max: 30, message: 'Entre 6 y 30 caracteres' },
          regexp: { regexp: /^[a-zA-Z0-9 ]+$/, message: 'Solo alfanumérico y espacios' }
        }
      }
    },
    plugins: {
      trigger: new FormValidation.plugins.Trigger(),
      bootstrap5: new FormValidation.plugins.Bootstrap5({ eleValidClass: '', rowSelector: '.col-sm-6' }),
      autoFocus: new FormValidation.plugins.AutoFocus(),
      submitButton: new FormValidation.plugins.SubmitButton()
    },
    init: instance => {
      instance.on('plugins.message.placed', function (e) {
        if (e.element.parentElement.classList.contains('input-group')) {
          e.element.parentElement.insertAdjacentElement('afterend', e.messageElement);
        }
      });
    }
  }).on('core.form.valid', function () { validationStepper.next(); });

  // --- Paso 2
  const FormValidation2 = FormValidation.formValidation(step2, {
    fields: {
      formValidationNombreResponsable1: { selector: '#formValidationNombreResponsable1', validators: { notEmpty: { message: 'El dato es requerido' } } },
      formValidationApResponsable1:     { selector: '#formValidationApResponsable1',     validators: { notEmpty: { message: 'El dato es requerido' } } },
      formValidationAmResponsable1:     { selector: '#formValidationAmResponsable1',     validators: { notEmpty: { message: 'El dato es requerido' } } },
      formValidationCiResponsable1:     { selector: '#formValidationCiResponsable1',     validators: { notEmpty: { message: 'El dato es requerido' } } },
      formValidationCorreoResponsable1: { selector: '#formValidationCorreoResponsable1', validators: { notEmpty: { message: 'El correo es requerido' }, emailAddress: { message: 'No es un correo válido' } } },
      formValidationCelularResponsable1:{ selector: '#formValidationCelularResponsable1',validators: { notEmpty: { message: 'El dato es requerido' } } }
    },
    plugins: {
      trigger: new FormValidation.plugins.Trigger(),
      bootstrap5: new FormValidation.plugins.Bootstrap5({ eleValidClass: '', rowSelector: '.col-sm-4' }),
      autoFocus: new FormValidation.plugins.AutoFocus(),
      submitButton: new FormValidation.plugins.SubmitButton()
    }
  }).on('core.form.valid', function () { validationStepper.next(); });

  // --- Paso 3 (Puestos) → solo avanza
  const FormValidation3 = FormValidation.formValidation(step3, {
    fields: {
      // Si quieres validar categoría / puestos, decláralos aquí
    },
    plugins: {
      trigger: new FormValidation.plugins.Trigger(),
      bootstrap5: new FormValidation.plugins.Bootstrap5({ eleValidClass: '', rowSelector: '.col-sm-6' }),
      autoFocus: new FormValidation.plugins.AutoFocus(),
      submitButton: new FormValidation.plugins.SubmitButton()
    }
  }).on('core.form.valid', function () { validationStepper.next(); });

  // --- Paso 4 (Pago) → aquí sí se envía
  const FormValidation4 = FormValidation.formValidation(step4, {
    fields: {
      // Ejemplo si exiges numComprobante cuando NO es pago contado:
      // numComprobante: {
      //   validators: { digits: { message: 'Solo números' } }
      // }
    },
    plugins: {
      trigger: new FormValidation.plugins.Trigger(),
      bootstrap5: new FormValidation.plugins.Bootstrap5({ eleValidClass: '', rowSelector: '.col-sm-6' }),
      autoFocus: new FormValidation.plugins.AutoFocus(),
      submitButton: new FormValidation.plugins.SubmitButton()
    }
  }).on('core.form.valid', function () { wizardValidationForm.submit(); });

  // NEXT (un solo handler)
  btnNext.forEach(item => {
    item.addEventListener('click', e => {
      e.preventDefault(); // por si algún botón quedó type="submit"
      switch (validationStepper._currentIndex) {
        case 0: FormValidation1.validate(); break;
        case 1: FormValidation2.validate(); break;
        case 2: FormValidation3.validate(); break;
        case 3: FormValidation4.validate(); break;
      }
    });
  });

  // PREV (un solo handler)
  btnPrev.forEach(item => {
    item.addEventListener('click', e => {
      e.preventDefault();
      if (validationStepper._currentIndex > 0) validationStepper.previous();
    });
  });

  // --- Opcional: inicialización selectpicker/select2 (ajusta tus field names si revalidas)
  if (selectPicker.length) {
    selectPicker.each(function () {
      $(this).selectpicker();
    });
  }
  if (select2.length) {
    select2.each(function () {
      const $this = $(this);
      $this.wrap('<div class="position-relative"></div>');
      $this.select2({ placeholder: 'Seleccione...', dropdownParent: $this.parent() });
    });
  }
})();
