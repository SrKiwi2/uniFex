/**
 *  Form Wizard
 */

'use strict';

(function () {
  const select2 = $('.select2'),
    selectPicker = $('.selectpicker');

  // Wizard Validation
  // --------------------------------------------------------------------
  const wizardValidation = document.querySelector('#wizard-validation');
  if (typeof wizardValidation !== undefined && wizardValidation !== null) {
    // Wizard form
    const wizardValidationForm = wizardValidation.querySelector('#wizard-validation-form');
    // Wizard steps
    const wizardValidationFormStep1 = wizardValidationForm.querySelector('#account-details-validation');
    const wizardValidationFormStep2 = wizardValidationForm.querySelector('#personal-info-validation');
    const wizardValidationFormStep3 = wizardValidationForm.querySelector('#social-links-validation');
    // Wizard next prev button
    const wizardValidationNext = [].slice.call(wizardValidationForm.querySelectorAll('.btn-next'));
    const wizardValidationPrev = [].slice.call(wizardValidationForm.querySelectorAll('.btn-prev'));

    const validationStepper = new Stepper(wizardValidation, {
      linear: true
    });

    // Account details
    const FormValidation1 = FormValidation.formValidation(wizardValidationFormStep1, {
      fields: {
        formValidationNombreEntidad: {
          selector: '#formValidationNombreEntidad',
          validators: {
            notEmpty: {
              message: 'El campo es requerido'
            },
            stringLength: {
              min: 6,
              max: 30,
              message: 'El dato debe tener entre 6 y 30 caracteres'
            },
            regexp: {
              regexp: /^[a-zA-Z0-9 ]+$/,
              message: 'El dato solo puede consistir en caracteres alfanuméricos y espacios'
            }
          }
        },
        formValidationNit: {
          selector: '#formValidationNit',
          validators: {
            notEmpty: {
              message: 'El campo es requerido'
            },
            stringLength: {
              min: 6,
              max: 30,
              message: 'El dato debe tener entre 6 y 30 caracteres'
            },
            regexp: {
              regexp: /^[a-zA-Z0-9 ]+$/,
              message: 'El dato solo puede consistir en caracteres alfanuméricos y espacios'
            }
          }
        },
        formValidationDescripcion: {
          selector: '#formValidationDescripcion',
          validators: {
            notEmpty: {
              message: 'El campo es requerido'
            },
            stringLength: {
              min: 6,
              max: 30,
              message: 'El dato debe tener entre 6 y 30 caracteres'
            },
            regexp: {
              regexp: /^[a-zA-Z0-9 ]+$/,
              message: 'El dato solo puede consistir en caracteres alfanuméricos y espacios'
            }
          }
        },
        
        // formValidationEmail: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Email is required'
        //     },
        //     emailAddress: {
        //       message: 'The value is not a valid email address'
        //     }
        //   }
        // },
        // formValidationPass: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The password is required'
        //     }
        //   }
        // },
        // formValidationConfirmPass: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Confirm Password is required'
        //     },
        //     identical: {
        //       compare: function () {
        //         return wizardValidationFormStep1.querySelector('[name="formValidationPass"]').value;
        //       },
        //       message: 'The password and its confirm are not the same'
        //     }
        //   }
        // }
      },
      plugins: {
        trigger: new FormValidation.plugins.Trigger(),
        bootstrap5: new FormValidation.plugins.Bootstrap5({
          // Use this for enabling/changing valid/invalid class
          // eleInvalidClass: '',
          eleValidClass: '',
          rowSelector: '.col-sm-6'
        }),
        autoFocus: new FormValidation.plugins.AutoFocus(),
        submitButton: new FormValidation.plugins.SubmitButton()
      },
      init: instance => {
        instance.on('plugins.message.placed', function (e) {
          //* Move the error message out of the `input-group` element
          if (e.element.parentElement.classList.contains('input-group')) {
            e.element.parentElement.insertAdjacentElement('afterend', e.messageElement);
          }
        });
      }
    }).on('core.form.valid', function () {
      // Jump to the next step when all fields in the current step are valid
      validationStepper.next();
    });

    // Personal info
    const FormValidation2 = FormValidation.formValidation(wizardValidationFormStep2, {
      fields: {
        formValidationNombreResponsable1: {
          selector: '#formValidationNombreResponsable1',
          validators: {
            notEmpty: {
              message: 'El dato es requerido'
            }
          }
        },
        formValidationApResponsable1: {
          selector: '#formValidationApResponsable1',
          validators: {
            notEmpty: {
              message: 'El dato es requerido'
            }
          }
        },
        formValidationAmResponsable1: {
          selector: '#formValidationAmResponsable1',
          validators: {
            notEmpty: {
              message: 'El dato es requerido'
            }
          }
        },
        formValidationCiResponsable1: {
          selector: '#formValidationCiResponsable1',
          validators: {
            notEmpty: {
              message: 'El dato es requerido'
            }
          }
        },
        formValidationCorreoResponsable1: {
          selector: '#formValidationCorreoResponsable1',
          validators: {
            notEmpty: {
              message: 'El correo es requerido'
            },
            emailAddress: {
              message: 'No es un correo válido'
            }
          }
        },
        formValidationCelularResponsable1: {
          selector: '#formValidationCelularResponsable1',
          validators: {
            notEmpty: {
              message: 'El dato es requerido'
            }
          }
        }
        
        // formValidationFirstName: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The first name is required'
        //     }
        //   }
        // },
        // formValidationLastName: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The last name is required'
        //     }
        //   }
        // },
        // formValidationCountry: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Country is required'
        //     }
        //   }
        // },
        // formValidationLanguage: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Languages is required'
        //     }
        //   }
        // }
      },
      plugins: {
        trigger: new FormValidation.plugins.Trigger(),
        bootstrap5: new FormValidation.plugins.Bootstrap5({
          // Use this for enabling/changing valid/invalid class
          // eleInvalidClass: '',
          eleValidClass: '',
          rowSelector: '.col-sm-4'
        }),
        autoFocus: new FormValidation.plugins.AutoFocus(),
        submitButton: new FormValidation.plugins.SubmitButton()
      }
    }).on('core.form.valid', function () {
      // Jump to the next step when all fields in the current step are valid
      validationStepper.next();
    });

    // Bootstrap Select (i.e Language select)
    if (selectPicker.length) {
      selectPicker.each(function () {
        var $this = $(this);
        $this.selectpicker().on('change', function () {
          FormValidation2.revalidateField('formValidationLanguage');
        });
      });
    }

    // select2
    if (select2.length) {
      select2.each(function () {
        var $this = $(this);
        $this.wrap('<div class="position-relative"></div>');
        $this
          .select2({
            placeholder: 'Select an country',
            dropdownParent: $this.parent()
          })
          .on('change', function () {
            // Revalidate the color field when an option is chosen
            FormValidation2.revalidateField('formValidationCountry');
          });
      });
    }

    // Social links
    const FormValidation3 = FormValidation.formValidation(wizardValidationFormStep3, {
      fields: {
        // formValidationTwitter: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Twitter URL is required'
        //     },
        //     uri: {
        //       message: 'The URL is not proper'
        //     }
        //   }
        // },
        // formValidationFacebook: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Facebook URL is required'
        //     },
        //     uri: {
        //       message: 'The URL is not proper'
        //     }
        //   }
        // },
        // formValidationGoogle: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The Google URL is required'
        //     },
        //     uri: {
        //       message: 'The URL is not proper'
        //     }
        //   }
        // },
        // formValidationLinkedIn: {
        //   validators: {
        //     notEmpty: {
        //       message: 'The LinkedIn URL is required'
        //     },
        //     uri: {
        //       message: 'The URL is not proper'
        //     }
        //   }
        // }
      },
      plugins: {
        trigger: new FormValidation.plugins.Trigger(),
        bootstrap5: new FormValidation.plugins.Bootstrap5({
          // Use this for enabling/changing valid/invalid class
          // eleInvalidClass: '',
          eleValidClass: '',
          rowSelector: '.col-sm-6'
        }),
        autoFocus: new FormValidation.plugins.AutoFocus(),
        submitButton: new FormValidation.plugins.SubmitButton()
      }
    }).on('core.form.valid', function () {
      wizardValidationForm.submit();
    });

    wizardValidationNext.forEach(item => {
      item.addEventListener('click', event => {
        // When click the Next button, we will validate the current step
        switch (validationStepper._currentIndex) {
          case 0:
            FormValidation1.validate();
            break;

          case 1:
            FormValidation2.validate();
            break;

          case 2:
            FormValidation3.validate();
            break;

          default:
            break;
        }
      });
    });

    wizardValidationPrev.forEach(item => {
      item.addEventListener('click', event => {
        switch (validationStepper._currentIndex) {
          case 2:
            validationStepper.previous();
            break;

          case 1:
            validationStepper.previous();
            break;

          case 0:

          default:
            break;
        }
      });
    });
  }
})();
